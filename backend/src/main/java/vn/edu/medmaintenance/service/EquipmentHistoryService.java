package vn.edu.medmaintenance.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.response.EquipmentHistoryResponse;
import vn.edu.medmaintenance.api.dto.response.EquipmentHistoryResponse.*;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class EquipmentHistoryService {
    private final EquipmentRepository equipment;
    private final MaintenancePlanItemRepository items;
    private final MaintenanceExecutionRepository executions;
    private final MaintenanceProgressLogRepository progress;
    private final AcceptanceRecordRepository acceptances;
    private final StatusHistoryRepository histories;
    private final MaintenanceReportRepository reports;
    private final CurrentUser currentUser;

    public EquipmentHistoryService(EquipmentRepository equipment, MaintenancePlanItemRepository items,
            MaintenanceExecutionRepository executions, MaintenanceProgressLogRepository progress,
            AcceptanceRecordRepository acceptances, StatusHistoryRepository histories,
            MaintenanceReportRepository reports, CurrentUser currentUser) {
        this.equipment = equipment;
        this.items = items;
        this.executions = executions;
        this.progress = progress;
        this.acceptances = acceptances;
        this.histories = histories;
        this.reports = reports;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public EquipmentHistoryResponse get(Long equipmentId) {
        var viewer = currentUser.get();
        if (viewer.role() != UserRole.PHONG_VTYT && viewer.role() != UserRole.BAN_GIAM_DOC
                && viewer.role() != UserRole.KHOA_PHONG)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "EQUIPMENT_HISTORY_ACCESS_DENIED",
                    "History role required");
        if (equipmentId == null || equipmentId <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Equipment ID must be positive");
        Equipment device = equipment.findWithDepartmentById(equipmentId).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "Equipment not found"));
        boolean departmentView = viewer.role() == UserRole.KHOA_PHONG;
        List<MaintenancePlanItem> campaigns = items.findHistoryByEquipmentId(equipmentId);
        if (departmentView) {
            Long departmentId = viewer.departmentId();
            boolean currentCustody = departmentId != null && departmentId.equals(device.getDepartment().getId());
            campaigns = campaigns.stream().filter(item -> departmentId != null
                    && departmentId.equals(item.getDepartmentAtPlan().getId())).toList();
            if (!currentCustody && campaigns.isEmpty())
                throw new BusinessRuleException(HttpStatus.FORBIDDEN, "EQUIPMENT_HISTORY_ACCESS_DENIED",
                        "Equipment is outside department scope");
        }
        if (campaigns.isEmpty())
            return new EquipmentHistoryResponse(device.getId(), device.getEquipmentCode(), device.getName(),
                    departmentView && !viewer.departmentId().equals(device.getDepartment().getId())
                            ? null : device.getDepartment().getId(), List.of());

        List<Long> itemIds = campaigns.stream().map(MaintenancePlanItem::getId).toList();
        List<Long> planIds = campaigns.stream().map(i -> i.getPlan().getId()).distinct().toList();
        List<MaintenanceExecution> attempts = executions.findHistoryByItemIds(itemIds);
        List<Long> executionIds = attempts.stream().map(MaintenanceExecution::getId).toList();

        Map<Long, List<MaintenanceExecution>> attemptsByItem = attempts.stream()
                .collect(Collectors.groupingBy(e -> e.getPlanItem().getId()));
        Map<Long, List<MaintenanceProgressLog>> progressByExecution = executionIds.isEmpty() ? Map.of()
                : progress.findHistoryByExecutionIds(executionIds).stream()
                        .collect(Collectors.groupingBy(l -> l.getExecution().getId()));
        Map<Long, List<AcceptanceRecord>> acceptanceByExecution = executionIds.isEmpty() ? Map.of()
                : acceptances.findHistoryByExecutionIds(executionIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getExecution().getId()));
        Map<Long, List<StatusHistory>> itemHistory = histories.findHistoryByItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(h -> h.getPlanItem().getId()));
        Map<Long, List<StatusHistory>> planHistory = departmentView ? Map.of()
                : histories.findHistoryByPlanIds(planIds).stream()
                        .collect(Collectors.groupingBy(h -> h.getPlan().getId()));
        Map<Long, MaintenanceReport> reportByPlan = reports.findHistoryByPlanIds(planIds).stream()
                .collect(Collectors.toMap(r -> r.getPlan().getId(), Function.identity()));

        List<Campaign> result = campaigns.stream().map(item -> {
            MaintenancePlan plan = item.getPlan();
            List<Attempt> work = attemptsByItem.getOrDefault(item.getId(), List.of()).stream().map(attempt -> {
                List<Progress> logs = progressByExecution.getOrDefault(attempt.getId(), List.of()).stream()
                        .map(log -> new Progress(log.getId(), log.getEventAt(), log.getWorkNote(),
                                log.getDamageNote(), log.getRecordedByUser().getId())).toList();
                Map<AcceptanceType, AcceptanceRecord> typed = acceptanceByExecution
                        .getOrDefault(attempt.getId(), List.of()).stream()
                        .collect(Collectors.toMap(AcceptanceRecord::getAcceptanceType, Function.identity()));
                return new Attempt(attempt.getId(), attempt.getAttemptNo(), attempt.getProvider().getId(),
                        attempt.getProvider().getName(), attempt.getStartedAt(), attempt.getEndedAt(),
                        attempt.getResultNote(), logs, mapAcceptance(typed.get(AcceptanceType.TECHNICAL_ACCEPTANCE)),
                        mapAcceptance(typed.get(AcceptanceType.HANDOVER_ACCEPTANCE)));
            }).toList();
            MaintenanceReport report = reportByPlan.get(plan.getId());
            ReportRef reportRef = report == null || (departmentView && report.getStatus() != ReportStatus.FINAL)
                    ? null : new ReportRef(report.getId(), report.getStatus(), report.getReportDate(),
                            report.getFinalizedAt());
            return new Campaign(plan.getId(), plan.getTitle(), plan.getStatus(),
                    plan.getPeriodStart(), plan.getPeriodEnd(), item.getId(),
                    item.getDepartmentAtPlan().getId(), item.getStatus(), item.getAssignmentRoute(),
                    departmentView || item.getAssignedProvider() == null ? null : item.getAssignedProvider().getId(),
                    departmentView || item.getCoverage() == null ? null : item.getCoverage().getId(),
                    work, itemHistory.getOrDefault(item.getId(), List.of()).stream()
                            .map(h -> mapState(h, departmentView)).toList(),
                    planHistory.getOrDefault(plan.getId(), List.of()).stream()
                            .map(h -> mapState(h, false)).toList(),
                    reportRef);
        }).toList();
        Long currentDepartmentId = departmentView
                && !viewer.departmentId().equals(device.getDepartment().getId())
                        ? null : device.getDepartment().getId();
        return new EquipmentHistoryResponse(device.getId(), device.getEquipmentCode(),
                device.getName(), currentDepartmentId, result);
    }

    private Acceptance mapAcceptance(AcceptanceRecord record) {
        if (record == null) return null;
        return new Acceptance(record.getId(), record.getAcceptanceType(), record.getResult(),
                record.getObservedAt(), record.getConclusion(), record.getRecordedByUser().getId(),
                record.getDepartmentConfirmedByUser() == null ? null : record.getDepartmentConfirmedByUser().getId(),
                record.getDepartmentConfirmedAt(),
                record.getVtytConfirmedByUser() == null ? null : record.getVtytConfirmedByUser().getId(),
                record.getVtytConfirmedAt());
    }

    private StateEvent mapState(StatusHistory history, boolean departmentView) {
        String reason = history.getReason();
        if (departmentView && !"REWORK_REQUIRED".equals(history.getNewState())
                && !"REPAIR_REQUIRED".equals(history.getNewState())) reason = null;
        return new StateEvent(history.getId(), history.getOldState(), history.getNewState(),
                history.getAction(), reason, history.getActionTimestamp(), history.getActorUser().getId());
    }
}
