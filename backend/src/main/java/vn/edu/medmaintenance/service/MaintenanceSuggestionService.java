package vn.edu.medmaintenance.service;

import java.time.*;
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
    private final MaintenanceAutomationService automation;
    public MaintenanceSuggestionService(EquipmentRepository e, MaintenancePlanItemRepository i, MaintenanceExecutionRepository x, AcceptanceRecordRepository a, MaintenanceCoverageRepository c, CurrentUser u, MaintenanceAutomationService automation) {
        equipment=e;
        items=i;
        executions=x;
        acceptances=a;
        coverages=c;
        current=u;this.automation=automation;
    }
    @Transactional(readOnly=true) public PageResponse<MaintenanceSuggestionResponse> list(Pageable page, String search, Boolean active, LocalDate referenceDate, Filters filters) {
        if (current.get().role()!=UserRole.PHONG_VTYT)throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Chỉ Phòng VTYT được xem đề xuất bảo trì.");
        String pattern="%"+(search==null?"":search.trim().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_"))+"%";
        var devices=equipment.searchVisible(filters.departmentId(), active, false, pattern, Pageable.unpaged(Sort.by("equipmentCode")));
        var ids=devices.getContent().stream().map(Equipment::getId).toList();
        if (ids.isEmpty())return PageResponse.from(new PageImpl<MaintenanceSuggestionResponse>(List.of(),page,0),r->r);
        var allItems=items.findByEquipment_IdIn(ids);
        var itemIds=allItems.stream().map(MaintenancePlanItem::getId).toList();
        var attempts=itemIds.isEmpty()?List.<MaintenanceExecution>of():executions.findHistoryByItemIds(itemIds);
        var attemptIds=attempts.stream().map(MaintenanceExecution::getId).toList();
        var records=attemptIds.isEmpty()?List.<AcceptanceRecord>of():acceptances.findHistoryByExecutionIds(attemptIds);
        var evidence=coverages.findByEquipment_IdIn(ids);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var mapped=devices.getContent().stream().map(d-> {
            var di=allItems.stream().filter(i->i.getEquipment().getId().equals(d.getId())).toList();
            var dx=attempts.stream().filter(x->x.getPlanItem().getEquipment().getId().equals(d.getId())).sorted(Comparator.comparing(MaintenanceExecution::getStartedAt).thenComparing(MaintenanceExecution::getId)).toList();
            var completed=dx.stream().filter(x->x.getEndedAt()!=null && records.stream().anyMatch(a->a.getExecution().getId().equals(x.getId()) && a.getAcceptanceType()==AcceptanceType.HANDOVER_ACCEPTANCE && a.getResult()==AcceptanceResult.PASS && a.getDepartmentConfirmedAt()!=null && a.getVtytConfirmedAt()!=null)).map(x->x.getEndedAt().atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()).distinct().sorted().toList();
            var open=di.stream().filter(i->!Set.of(PlanStatus.CLOSED, PlanStatus.REPORTED, PlanStatus.AWAITING_REPORT).contains(i.getPlan().getStatus()) && i.getStatus()!=PlanItemStatus.COMPLETED && i.getStatus()!=PlanItemStatus.REPAIR_REQUIRED).toList();
            LocalDate last=completed.isEmpty()?null:completed.get(completed.size()-1);
            LocalDate next=PeriodicSchedule.next(Boolean.TRUE.equals(d.getMaintenanceEnabled()),d.getMaintenanceIntervalValue(),d.getMaintenanceIntervalUnit(),last,d.getCommissioningDate());
            String basis=next==null?"Cần cấu hình chu kỳ và ngày đưa vào sử dụng hoặc lịch sử hoàn tất":"Chu kỳ cấu hình tính từ "+(last==null?"ngày đưa vào sử dụng":"lần bảo trì hoàn tất gần nhất");
            LocalDate ref=referenceDate!=null?referenceDate:next==null || next.isBefore(today)?today:next;
            var eligible=PlanningDecisionService.eligibleCoverages(evidence,d.getId(),ref);
            var coverage=eligible.size()==1?eligible.get(0):null;
            var contract=coverage==null?null:coverage.getContract();
            var warranty=coverage!=null?coverage:evidence.stream().filter(c->c.getEquipment().getId().equals(d.getId()) && (c.getContractReference()!=null || c.getWarrantyExpiresOn()!=null))
                .max(Comparator.comparing((MaintenanceCoverage c)->c.getEffectiveFrom(), Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(MaintenanceCoverage::getId)).orElse(null);
            var manufacturer=d.getManufacturerProvider();
            var latest=dx.isEmpty()?null:dx.get(dx.size()-1);
            var record=latest==null?null:records.stream().filter(a->a.getExecution().getId().equals(latest.getId())).max(Comparator.comparing(AcceptanceRecord::getObservedAt).thenComparing(AcceptanceRecord::getId)).orElse(null);
            var external=dx.stream().filter(x->x.getPlanItem().getAssignmentRoute()==AssignmentRoute.EXTERNAL_APPROVED).reduce((a, b)->b).orElse(null);
            return new MaintenanceSuggestionResponse(d.getId(), d.getEquipmentCode(), d.getName(), d.getDepartment().getId(), d.getDepartment().getName(), completed.isEmpty()?null:completed.get(completed.size()-1), record==null?null:record.getResult().name(), latest==null?null:latest.getPlanItem().getStatus().name(), coverage==null?CoverageClassification.NOT_FREE:CoverageClassification.FREE, coverage==null?null:coverage.getId(), coverage==null?null:coverage.getContractReference(), coverage==null?null:coverage.getProvider().getName(), external==null?null:external.getProvider().getName(), next, ref, basis, coverage==null?"Không có hợp đồng hợp lệ vào ngày tham chiếu; chuẩn bị đề xuất ngoài hợp đồng.":"Hợp đồng đã xác minh, áp dụng vào ngày tham chiếu.", open.stream().map(i->i.getPlan().getId()).distinct().sorted().toList(), warranty==null?null:warranty.getWarrantyExpiresOn(), WarrantyStatus.at(warranty, ref), manufacturer==null?null:manufacturer.getId(), manufacturer==null?null:manufacturer.getName(), manufacturer==null?null:manufacturer.getContactDetails(), d.getModel(), d.getSerialNumber(), d.getTechnicalSpec(), d.getActive(),d.getMaintenanceIntervalValue(),d.getMaintenanceIntervalUnit(),d.getMaintenanceEnabled(),next,
                PeriodicSchedule.status(next,today,automation.soonDays()),contract==null?null:contract.getId(),coverage==null?null:coverage.getProvider().getId(),
                coverage==null?null:coverage.getEffectiveFrom(),coverage==null?null:coverage.getEffectiveTo(),eligible.size()>1?"CONFLICT":coverage==null?"NONE":"ACTIVE");
        }).filter(r->matches(r,filters)).toList();
        var sorted=new ArrayList<>(mapped);
        Comparator<MaintenanceSuggestionResponse> comparator=(a,b)->0;
        for(var order:page.getSort()){
          Comparator<MaintenanceSuggestionResponse> c=switch(order.getProperty()){
            case "nextMaintenanceDueDate" -> Comparator.comparing(MaintenanceSuggestionResponse::nextMaintenanceDueDate,Comparator.nullsLast(Comparator.naturalOrder()));
            case "id" -> Comparator.comparing(MaintenanceSuggestionResponse::equipmentId);
            default -> Comparator.comparing(MaintenanceSuggestionResponse::equipmentCode);
          };comparator=comparator.thenComparing(order.isDescending()?c.reversed():c);
        }
        sorted.sort(comparator.thenComparing(MaintenanceSuggestionResponse::equipmentCode));
        int start=(int)Math.min(page.getOffset(),sorted.size()),end=Math.min(start+page.getPageSize(),sorted.size());
        return PageResponse.from(new PageImpl<>(sorted.subList(start,end),page,sorted.size()),r->r);
    }
    public record Filters(Long departmentId,LocalDate dueFrom,LocalDate dueTo,String dueStatus,CoverageClassification classification,Long providerId,Boolean notInOpenPlan){}
    private boolean matches(MaintenanceSuggestionResponse r,Filters f){
      return (f.dueFrom()==null || r.nextMaintenanceDueDate()!=null && !r.nextMaintenanceDueDate().isBefore(f.dueFrom()))
        && (f.dueTo()==null || r.nextMaintenanceDueDate()!=null && !r.nextMaintenanceDueDate().isAfter(f.dueTo()))
        && (f.dueStatus()==null || f.dueStatus().equals(r.dueStatus()))
        && (f.classification()==null || f.classification()==r.classification())
        && (f.providerId()==null || f.providerId().equals(r.contractualProviderId()))
        && (f.notInOpenPlan()==null || f.notInOpenPlan()==r.openPlanIds().isEmpty());
    }
    @Transactional(readOnly=true) public PageResponse<MaintenanceSuggestionResponse> list(Pageable page,String search,Boolean active,LocalDate ref){return list(page,search,active,ref,new Filters(null,null,null,null,null,null,null));}
}
