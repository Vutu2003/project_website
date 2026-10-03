package vn.edu.medmaintenance.service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.api.dto.response.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service public class MaintenanceSuggestionService {
    private final EquipmentRepository equipment;
    private final MaintenancePlanItemRepository items;
    private final MaintenanceExecutionRepository executions;
    private final AcceptanceRecordRepository acceptances;
    private final MaintenanceCoverageRepository coverages;
    private final CurrentUser current;
    public MaintenanceSuggestionService(EquipmentRepository e, MaintenancePlanItemRepository i, MaintenanceExecutionRepository x, AcceptanceRecordRepository a, MaintenanceCoverageRepository c, CurrentUser u) {
        equipment=e;
        items=i;
        executions=x;
        acceptances=a;
        coverages=c;
        current=u;
    }
    @Transactional(readOnly=true) public PageResponse<MaintenanceSuggestionResponse> list(Pageable page, String search, Boolean active, LocalDate referenceDate) {
        if (current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Chỉ Phòng VTYT được xem đề xuất bảo trì.");
        String pattern="%"+(search==null?"":search.trim().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_"))+"%";
        var devices=equipment.searchVisible(null, active, false, pattern, page);
        var ids=devices.getContent().stream().map(Equipment::getId).toList();
        if (ids.isEmpty())return PageResponse.from(devices, d->null);
        var allItems=items.findByEquipment_IdIn(ids);
        var itemIds=allItems.stream().map(MaintenancePlanItem::getId).toList();
        var attempts=itemIds.isEmpty()?List.<MaintenanceExecution>of():executions.findHistoryByItemIds(itemIds);
        var attemptIds=attempts.stream().map(MaintenanceExecution::getId).toList();
        var records=attemptIds.isEmpty()?List.<AcceptanceRecord>of():acceptances.findHistoryByExecutionIds(attemptIds);
        var evidence=coverages.findByEquipment_IdIn(ids);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        return PageResponse.from(devices, d-> {
            var di=allItems.stream().filter(i->i.getEquipment().getId().equals(d.getId())).toList();
            var dx=attempts.stream().filter(x->x.getPlanItem().getEquipment().getId().equals(d.getId())).sorted(Comparator.comparing(MaintenanceExecution::getStartedAt).thenComparing(MaintenanceExecution::getId)).toList();
            var completed=dx.stream().filter(x->x.getEndedAt()!=null && records.stream().anyMatch(a->a.getExecution().getId().equals(x.getId()) && a.getAcceptanceType()==AcceptanceType.HANDOVER_ACCEPTANCE && a.getResult()==AcceptanceResult.PASS)).map(x->x.getEndedAt().atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()).distinct().sorted().toList();
            var open=di.stream().filter(i->!Set.of(PlanStatus.CLOSED, PlanStatus.REPORTED, PlanStatus.AWAITING_REPORT).contains(i.getPlan().getStatus()) && i.getStatus()!=PlanItemStatus.COMPLETED && i.getStatus()!=PlanItemStatus.REPAIR_REQUIRED).toList();
            LocalDate next=open.stream().map(MaintenancePlanItem::getPlannedDate).filter(Objects::nonNull).filter(t->!t.isBefore(today)).min(LocalDate::compareTo).orElse(null);
            String basis=next==null?"Chưa đủ dữ liệu để đề xuất thời gian":"Ngày dự kiến trong kế hoạch đang mở";
            if (next==null && completed.size()>=2) {
                long days=observedInterval(completed);
                next=completed.get(completed.size()-1).plusDays(days);
                basis="Suy ra từ "+completed.size()+" lần bảo trì hoàn tất; khoảng cách trung bình "+days+" ngày";
            }
            LocalDate ref=referenceDate!=null?referenceDate:next==null?today:next;
            var coverage=evidence.stream().filter(c->PlanningDecisionService.validFree(c, d.getId(), ref)).max(Comparator.comparing(MaintenanceCoverage::getId)).orElse(null);
            var warranty=coverage!=null?coverage:evidence.stream().filter(c->c.getEquipment().getId().equals(d.getId()) && (c.getContractReference()!=null || c.getWarrantyExpiresOn()!=null))
                .max(Comparator.comparing((MaintenanceCoverage c)->c.getEffectiveFrom(), Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(MaintenanceCoverage::getId)).orElse(null);
            var manufacturer=d.getManufacturerProvider();
            var latest=dx.isEmpty()?null:dx.get(dx.size()-1);
            var record=latest==null?null:records.stream().filter(a->a.getExecution().getId().equals(latest.getId())).max(Comparator.comparing(AcceptanceRecord::getObservedAt).thenComparing(AcceptanceRecord::getId)).orElse(null);
            var external=dx.stream().filter(x->x.getPlanItem().getAssignmentRoute()==AssignmentRoute.EXTERNAL_APPROVED).reduce((a, b)->b).orElse(null);
            return new MaintenanceSuggestionResponse(d.getId(), d.getEquipmentCode(), d.getName(), d.getDepartment().getId(), d.getDepartment().getName(), completed.isEmpty()?null:completed.get(completed.size()-1), record==null?null:record.getResult().name(), latest==null?null:latest.getPlanItem().getStatus().name(), coverage==null?CoverageClassification.NOT_FREE:CoverageClassification.FREE, coverage==null?null:coverage.getId(), coverage==null?null:coverage.getContractReference(), coverage==null?null:coverage.getProvider().getName(), external==null?null:external.getProvider().getName(), next, ref, basis, coverage==null?"Không có hợp đồng hợp lệ vào ngày tham chiếu; chuẩn bị đề xuất ngoài hợp đồng.":"Hợp đồng đã xác minh, áp dụng vào ngày tham chiếu.", open.stream().map(i->i.getPlan().getId()).distinct().sorted().toList(), warranty==null?null:warranty.getWarrantyExpiresOn(), WarrantyStatus.at(warranty, ref), manufacturer==null?null:manufacturer.getId(), manufacturer==null?null:manufacturer.getName(), manufacturer==null?null:manufacturer.getContactDetails(), d.getModel(), d.getSerialNumber(), d.getTechnicalSpec(), d.getActive());
        });
    }
    static long observedInterval(List<LocalDate> dates) {
        return Math.max(1, Math.round((double)ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size()-1))/(dates.size()-1)));
    }
}
