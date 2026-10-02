package vn.edu.medmaintenance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.FinalizeReportRequest;
import vn.edu.medmaintenance.api.dto.request.SaveReportRequest;
import vn.edu.medmaintenance.api.dto.response.ReportResponse;
import vn.edu.medmaintenance.api.dto.response.ReportEvidenceResponse;
import vn.edu.medmaintenance.api.dto.response.EquipmentHistoryResponse;
import vn.edu.medmaintenance.persistence.entity.AcceptanceRecord;
import vn.edu.medmaintenance.persistence.entity.MaintenanceExecution;
import vn.edu.medmaintenance.persistence.entity.MaintenanceProgressLog;
import vn.edu.medmaintenance.persistence.enums.AcceptanceType;
import vn.edu.medmaintenance.persistence.repository.MaintenanceExecutionRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenanceProgressLogRepository;
import vn.edu.medmaintenance.persistence.repository.AcceptanceRecordRepository;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.MaintenanceReport;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.ReportStatus;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanItemRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenanceReportRepository;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class MaintenanceReportService {
    private final MaintenancePlanRepository plans;
    private final MaintenancePlanItemRepository items;
    private final MaintenanceReportRepository reports;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final WorkflowHistory history;
    private final EntityManager entityManager;
    private final NotificationService notifications;
    private final MaintenanceExecutionRepository executions;
    private final MaintenanceProgressLogRepository progress;
    private final AcceptanceRecordRepository acceptances;

    public MaintenanceReportService(MaintenancePlanRepository plans, MaintenancePlanItemRepository items,
            MaintenanceReportRepository reports, UserAccountRepository users,
            CurrentUser currentUser, WorkflowHistory history, EntityManager entityManager,NotificationService notifications,
            MaintenanceExecutionRepository executions, MaintenanceProgressLogRepository progress,
            AcceptanceRecordRepository acceptances) {
        this.plans = plans;
        this.items = items;
        this.reports = reports;
        this.users = users;
        this.currentUser = currentUser;
        this.history = history;
        this.entityManager = entityManager;this.notifications=notifications;
        this.executions = executions;
        this.progress = progress;
        this.acceptances = acceptances;
    }

    @Transactional
    public ReportResponse create(Long planId, SaveReportRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenancePlan plan = lockedPlan(planId);
        checkVersion(plan, command.version());
        if (reports.findByPlan_Id(planId).isPresent())
            conflict("REPORT_ALREADY_EXISTS", "This plan already has a report");
        List<MaintenancePlanItem> planItems = requireReportable(plan);
        MaintenanceReport report = new MaintenanceReport();
        report.setPlan(plan);
        report.setCreatedByUser(actor);
        report.setReportDate(LocalDate.now(ZoneOffset.UTC));
        report.setStatus(ReportStatus.DRAFT);
        fill(report, command);
        reports.save(report);
        entityManager.lock(plan, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        return response(report, plan, planItems);
    }

    @Transactional
    public ReportResponse edit(Long planId, SaveReportRequest command) {
        requireRole(UserRole.PHONG_VTYT);
        MaintenancePlan plan = lockedPlan(planId);
        checkVersion(plan, command.version());
        MaintenanceReport report = findReport(planId);
        if (report.getStatus() != ReportStatus.DRAFT)
            conflict("REPORT_NOT_EDITABLE", "Only draft reports can be edited");
        List<MaintenancePlanItem> planItems = requireReportable(plan);
        fill(report, command);
        entityManager.lock(plan, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        return response(report, plan, planItems);
    }

    @Transactional
    public ReportResponse finalizeReport(Long planId, FinalizeReportRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenancePlan plan = lockedPlan(planId);
        checkVersion(plan, command.version());
        MaintenanceReport report = findReport(planId);
        if (report.getStatus() != ReportStatus.DRAFT)
            conflict("REPORT_NOT_FINALIZABLE", "Report is already final");
        List<MaintenancePlanItem> planItems = requireReportable(plan);
        if (blank(report.getWorkDone()))
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "REPORT_WORK_DONE_REQUIRED",
                    "Final report requires a work-done narrative");
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        report.setStatus(ReportStatus.FINAL);
        report.setFinalizedAt(at);
        plan.setStatus(PlanStatus.REPORTED);
        history.plan(plan, actor, "AWAITING_REPORT", "REPORTED", "FINALIZE_REPORT", null, at);
        notifications.notifyRole(UserRole.BAN_GIAM_DOC,null,actor,"REPORT_FINALIZED","Báo cáo kết quả bảo trì đã được lập",plan.getTitle(),"/plans/"+planId+"/report");
        entityManager.flush();
        return response(report, plan, planItems);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(Long planId) {
        requireReader();
        MaintenancePlan plan = findPlan(planId);
        MaintenanceReport report = findReport(planId);
        return response(report, plan, items.findAllByPlan_Id(planId));
    }

    @Transactional(readOnly = true)
    public ReportEvidenceResponse evidence(Long planId) {
        requireReader();
        findPlan(planId);
        var planItems = items.findReportItems(planId);
        var itemIds = planItems.stream().map(MaintenancePlanItem::getId).toList();
        var attempts = itemIds.isEmpty() ? List.<MaintenanceExecution>of() : executions.findHistoryByItemIds(itemIds);
        var executionIds = attempts.stream().map(MaintenanceExecution::getId).toList();
        var logs = executionIds.isEmpty() ? List.<MaintenanceProgressLog>of() : progress.findHistoryByExecutionIds(executionIds);
        var records = executionIds.isEmpty() ? List.<AcceptanceRecord>of() : acceptances.findHistoryByExecutionIds(executionIds);
        var attemptsByItem = attempts.stream().collect(Collectors.groupingBy(e -> e.getPlanItem().getId()));
        var logsByExecution = logs.stream().collect(Collectors.groupingBy(l -> l.getExecution().getId()));
        var recordsByExecution = records.stream().collect(Collectors.groupingBy(a -> a.getExecution().getId()));
        return new ReportEvidenceResponse(planId, planItems.stream().map(item -> {
            var evidence = attemptsByItem.getOrDefault(item.getId(), List.of()).stream().map(attempt -> {
                var updates = logsByExecution.getOrDefault(attempt.getId(), List.of()).stream()
                        .map(log -> new EquipmentHistoryResponse.Progress(log.getId(), log.getEventAt(),
                                log.getWorkNote(), log.getDamageNote(), log.getRecordedByUser().getId())).toList();
                var results = recordsByExecution.getOrDefault(attempt.getId(), List.of()).stream()
                        .collect(Collectors.toMap(AcceptanceRecord::getAcceptanceType, a -> a));
                return new EquipmentHistoryResponse.Attempt(attempt.getId(), attempt.getAttemptNo(),
                        attempt.getProvider().getId(), attempt.getProvider().getName(), attempt.getStartedAt(),
                        attempt.getEndedAt(), attempt.getResultNote(), updates,
                        evidenceAcceptance(results.get(AcceptanceType.TECHNICAL_ACCEPTANCE)),
                        evidenceAcceptance(results.get(AcceptanceType.HANDOVER_ACCEPTANCE)));
            }).toList();
            return new ReportEvidenceResponse.Item(item.getId(), item.getEquipment().getEquipmentCode(),
                    item.getEquipment().getName(), item.getDepartmentAtPlan().getName(), item.getStatus(),
                    item.getAssignedProvider() == null ? null : item.getAssignedProvider().getName(), evidence);
        }).toList());
    }

    private EquipmentHistoryResponse.Acceptance evidenceAcceptance(AcceptanceRecord record) {
        if (record == null) return null;
        return new EquipmentHistoryResponse.Acceptance(record.getId(), record.getAcceptanceType(), record.getResult(),
                record.getObservedAt(), record.getConclusion(), record.getRecordedByUser().getId(),
                record.getDepartmentConfirmedByUser() == null ? null : record.getDepartmentConfirmedByUser().getId(),
                record.getDepartmentConfirmedAt(),
                record.getVtytConfirmedByUser() == null ? null : record.getVtytConfirmedByUser().getId(),
                record.getVtytConfirmedAt());
    }

    private void requireReader() {
        var role = currentUser.get().role();
        if (role != UserRole.PHONG_VTYT && role != UserRole.BAN_GIAM_DOC)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Report role required");
    }

    private MaintenancePlan lockedPlan(Long id) {
        MaintenancePlan plan = findPlan(id);
        entityManager.lock(plan, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(plan);
        return plan;
    }

    private MaintenancePlan findPlan(Long id) {
        if (id == null || id <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Plan ID must be positive");
        return plans.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found"));
    }

    private MaintenanceReport findReport(Long planId) {
        return reports.findByPlan_Id(planId).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found"));
    }

    private List<MaintenancePlanItem> requireReportable(MaintenancePlan plan) {
        if (plan.getStatus() != PlanStatus.AWAITING_REPORT)
            conflict("PLAN_NOT_REPORTABLE", "Plan is not awaiting a report");
        List<MaintenancePlanItem> planItems = items.findAllByPlan_Id(plan.getId());
        if (planItems.isEmpty() || planItems.stream().anyMatch(item ->
                item.getStatus() != PlanItemStatus.COMPLETED
                        && item.getStatus() != PlanItemStatus.REPAIR_REQUIRED))
            conflict("PLAN_NOT_REPORTABLE", "All plan items need a final V1 outcome");
        return planItems;
    }

    private void fill(MaintenanceReport report, SaveReportRequest command) {
        report.setReportNumber(optional(command.reportNumber()));
        report.setWorkDone(optional(command.workDone()));
        report.setAchieved(optional(command.achieved()));
        report.setNotAchieved(optional(command.notAchieved()));
        report.setCauses(optional(command.causes()));
        report.setNextWork(optional(command.nextWork()));
        report.setResolutions(optional(command.resolutions()));
        report.setRecommendations(optional(command.recommendations()));
    }

    private ReportResponse response(MaintenanceReport report, MaintenancePlan plan,
            List<MaintenancePlanItem> planItems) {
        long completed = planItems.stream().filter(i -> i.getStatus() == PlanItemStatus.COMPLETED).count();
        long repair = planItems.stream().filter(i -> i.getStatus() == PlanItemStatus.REPAIR_REQUIRED).count();
        return new ReportResponse(report.getId(), plan.getId(), report.getStatus(), plan.getStatus(),
                plan.getVersion(), report.getReportDate(), report.getFinalizedAt(),
                report.getReportNumber(), report.getWorkDone(), report.getAchieved(), report.getNotAchieved(),
                report.getCauses(), report.getNextWork(), report.getResolutions(), report.getRecommendations(),
                completed, repair);
    }

    private UserAccount requireRole(UserRole role) {
        var principal = currentUser.get();
        if (principal.role() != role)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Report role required");
        return users.getReferenceById(principal.id());
    }

    private void checkVersion(MaintenancePlan plan, Integer expected) {
        if (expected == null || !plan.getVersion().equals(expected))
            conflict("OPTIMISTIC_LOCK_CONFLICT", "Plan has changed; reload before retrying");
    }

    private String optional(String value) { return value == null ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void conflict(String code, String message) {
        throw new BusinessRuleException(HttpStatus.CONFLICT, code, message);
    }
}
