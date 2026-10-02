package vn.edu.medmaintenance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.*;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.security.auth.VtytCoSigner;

@Service
public class ExecutionAcceptanceService {
    private final MaintenancePlanItemRepository items;
    private final MaintenanceExecutionRepository executions;
    private final MaintenanceProgressLogRepository progress;
    private final AcceptanceRecordRepository acceptances;
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final VtytCoSigner coSigner;
    private final WorkflowHistory history;
    private final EntityManager entityManager;
    private final NotificationService notifications;
    private final StatusHistoryRepository histories;

    public ExecutionAcceptanceService(MaintenancePlanItemRepository items,
            MaintenanceExecutionRepository executions, MaintenanceProgressLogRepository progress,
            AcceptanceRecordRepository acceptances, ApprovalRequestRepository requests,
            ApprovalActionRepository actions, UserAccountRepository users, CurrentUser currentUser,
            VtytCoSigner coSigner, WorkflowHistory history, EntityManager entityManager,NotificationService notifications, StatusHistoryRepository histories) {
        this.items = items;
        this.executions = executions;
        this.progress = progress;
        this.acceptances = acceptances;
        this.requests = requests;
        this.actions = actions;
        this.users = users;
        this.currentUser = currentUser;
        this.coSigner = coSigner;
        this.history = history;
        this.entityManager = entityManager;this.notifications=notifications;this.histories=histories;
    }

    @Transactional
    public ExecutionWorkflowResponse start(Long itemId, StartExecutionRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenancePlanItem item = lockedItem(itemId);
        MaintenancePlan plan = item.getPlan();
        checkVersion(item, command.version());
        if (plan.getStatus() != PlanStatus.APPROVED && plan.getStatus() != PlanStatus.IN_PROGRESS)
            conflict("PLAN_NOT_EXECUTABLE", "Plan is not approved for maintenance");
        if (!plan.getVersion().equals(command.planVersion()))
            conflict("OPTIMISTIC_LOCK_CONFLICT", "Plan has changed; reload before retrying");
        // Prepared vendor decisions must finish before execution, so a revision can
        // safely return the entire plan to its editable state. Legacy plans retain their gate.
        if (histories.existsByPlanItem_Plan_IdAndPlanItem_StatusAndAction(plan.getId(),
                PlanItemStatus.WAITING_VENDOR_APPROVAL, "ACTIVATE_PREPARED_VENDOR"))
            conflict("PLAN_VENDOR_APPROVAL_PENDING", "Cần phê duyệt xong các đơn vị đề xuất trước khi bắt đầu thực hiện kế hoạch.");
        PlanItemStatus old = item.getStatus();
        if (old != PlanItemStatus.UNDER_CONTRACT && old != PlanItemStatus.ASSIGNED_EXTERNAL
                && old != PlanItemStatus.REWORK_REQUIRED)
            conflict("PLAN_ITEM_NOT_EXECUTABLE", "Plan item is not ready for an execution attempt");
        if ((old == PlanItemStatus.UNDER_CONTRACT && item.getAssignmentRoute() != AssignmentRoute.UNDER_CONTRACT)
                || (old == PlanItemStatus.ASSIGNED_EXTERNAL
                        && item.getAssignmentRoute() != AssignmentRoute.EXTERNAL_APPROVED))
            conflict("INVALID_PROVIDER_ROUTE", "Item state and assignment route do not match");
        ServiceProvider provider = routeProvider(item);
        MaintenanceExecution previous = executions.findFirstByPlanItem_IdOrderByAttemptNoDesc(itemId).orElse(null);
        if (old == PlanItemStatus.REWORK_REQUIRED) {
            if (previous == null || previous.getEndedAt() == null)
                conflict("REWORK_NOT_ALLOWED", "Previous execution must have finished before rework");
        } else if (previous != null) {
            conflict("EXECUTION_ALREADY_EXISTS", "First execution already exists for this item");
        }
        OffsetDateTime now = now();
        MaintenanceExecution attempt = new MaintenanceExecution();
        attempt.setPlanItem(item);
        attempt.setProvider(provider);
        attempt.setStartedByUser(actor);
        attempt.setAttemptNo(previous == null ? 1 : previous.getAttemptNo() + 1);
        attempt.setStartedAt(now);
        executions.save(attempt);
        item.setStatus(PlanItemStatus.IN_MAINTENANCE);
        history.itemTransition(item, actor, old.name(), PlanItemStatus.IN_MAINTENANCE.name(),
                old == PlanItemStatus.REWORK_REQUIRED ? "START_REWORK" : "START_MAINTENANCE", null, now);
        if (plan.getStatus() == PlanStatus.APPROVED) {
            plan.setStatus(PlanStatus.IN_PROGRESS);
            history.plan(plan, actor, "APPROVED", "IN_PROGRESS", "START_MAINTENANCE", null, now);
        }
        entityManager.flush();
        return executionResponse(attempt, item);
    }

    @Transactional
    public ProgressResponse addProgress(Long executionId, ProgressRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenanceExecution attempt = findExecution(executionId);
        MaintenancePlanItem item = lockedItem(attempt.getPlanItem().getId());
        if (command.version() != null) checkVersion(item, command.version());
        requireCurrent(attempt, item);
        if (item.getStatus() != PlanItemStatus.IN_MAINTENANCE || attempt.getEndedAt() != null)
            conflict("EXECUTION_NOT_ACTIVE", "Execution is not active");
        MaintenanceProgressLog log = new MaintenanceProgressLog();
        log.setExecution(attempt);
        log.setRecordedByUser(actor);
        log.setEventAt(now());
        // Reuse the existing append-only text column. Its first line is the predefined
        // Vietnamese status; remaining lines hold the optional note. Legacy notes stay readable.
        String note = optional(command.note());
        log.setWorkNote(command.status() == null ? required(command.workNote(), "WORK_NOTE_REQUIRED")
                : command.status().label() + (blank(note) ? "" : "\n" + note));
        log.setDamageNote(optional(command.damageNote()));
        progress.save(log);
        entityManager.flush();
        return new ProgressResponse(log.getId(), attempt.getId(), log.getEventAt());
    }

    @Transactional
    public ExecutionWorkflowResponse finish(Long executionId, FinishExecutionRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenanceExecution attempt = findExecution(executionId);
        MaintenancePlanItem item = lockedItem(attempt.getPlanItem().getId());
        checkVersion(item, command.version());
        requireCurrent(attempt, item);
        if (item.getStatus() != PlanItemStatus.IN_MAINTENANCE || attempt.getEndedAt() != null)
            conflict("EXECUTION_NOT_ACTIVE", "Execution is not active");
        OffsetDateTime at = now();
        attempt.setEndedAt(at);
        attempt.setResultNote(optional(command.resultNote()));
        transition(item, actor, PlanItemStatus.AWAITING_TECHNICAL_ACCEPTANCE, "FINISH_MAINTENANCE", null, at);
        entityManager.flush();
        return executionResponse(attempt, item);
    }

    @Transactional
    public ExecutionWorkflowResponse repair(Long executionId, RepairHandoffRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenanceExecution attempt = findExecution(executionId);
        MaintenancePlanItem item = lockedItem(attempt.getPlanItem().getId());
        checkVersion(item, command.version());
        requireCurrent(attempt, item);
        if (item.getStatus() != PlanItemStatus.IN_MAINTENANCE || attempt.getEndedAt() != null)
            conflict("REPAIR_HANDOFF_NOT_ALLOWED", "Only active work may be handed off for repair");
        String reason = required(command.reason(), "REPAIR_REASON_REQUIRED");
        OffsetDateTime at = now();
        MaintenanceProgressLog log = new MaintenanceProgressLog();
        log.setExecution(attempt);
        log.setRecordedByUser(actor);
        log.setEventAt(at);
        log.setWorkNote("Phát hiện hư hỏng, chuyển xử lý sửa chữa");
        log.setDamageNote(reason);
        progress.save(log);
        attempt.setEndedAt(at);
        attempt.setResultNote(reason);
        transition(item, actor, PlanItemStatus.REPAIR_REQUIRED, "REPAIR_HANDOFF", reason, at);
        reportReady(item, actor, at);
        entityManager.flush();
        return executionResponse(attempt, item);
    }

    @Transactional
    public AcceptanceWorkflowResponse technical(Long executionId, TechnicalAcceptanceRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenanceExecution attempt = findExecution(executionId);
        MaintenancePlanItem item = lockedItem(attempt.getPlanItem().getId());
        checkVersion(item, command.version());
        requireCurrent(attempt, item);
        if (acceptances.findByExecution_IdAndAcceptanceType(executionId, AcceptanceType.TECHNICAL_ACCEPTANCE).isPresent())
            conflict("TECHNICAL_ACCEPTANCE_EXISTS", "Technical acceptance already recorded");
        if (item.getStatus() != PlanItemStatus.AWAITING_TECHNICAL_ACCEPTANCE || attempt.getEndedAt() == null)
            conflict("EXECUTION_NOT_READY_FOR_ACCEPTANCE", "Execution is not ready for technical acceptance");
        String conclusion = required(command.conclusion(), "CONCLUSION_REQUIRED");
        if (command.result() == AcceptanceResult.PASS && command.repairRequired())
            conflict("INVALID_ACCEPTANCE_OUTCOME", "PASS cannot require repair");
        OffsetDateTime at = now();
        AcceptanceRecord record = acceptance(attempt, actor, AcceptanceType.TECHNICAL_ACCEPTANCE,
                command.result(), conclusion, at);
        acceptances.save(record);
        PlanItemStatus next = command.result() == AcceptanceResult.PASS
                ? PlanItemStatus.AWAITING_HANDOVER
                : command.repairRequired() ? PlanItemStatus.REPAIR_REQUIRED : PlanItemStatus.REWORK_REQUIRED;
        transition(item, actor, next, "TECHNICAL_ACCEPTANCE", command.result() == AcceptanceResult.FAIL ? conclusion : null, at);
        if(next==PlanItemStatus.AWAITING_HANDOVER)notifications.notifyRole(UserRole.KHOA_PHONG,item.getDepartmentAtPlan().getId(),actor,"HANDOVER_PENDING","Thiết bị chờ bàn giao",item.getEquipment().getEquipmentCode(),"/plans/"+item.getPlan().getId()+"/items/"+item.getId()+"/execution");
        if (next == PlanItemStatus.REPAIR_REQUIRED) reportReady(item, actor, at);
        entityManager.flush();
        return acceptanceResponse(record, item);
    }

    @Transactional
    public AcceptanceWorkflowResponse handover(Long executionId, String vtytAuthorization,
            HandoverRequest command) {
        UserAccount actor = requireRole(UserRole.KHOA_PHONG);
        MaintenanceExecution attempt = findExecution(executionId);
        MaintenancePlanItem item = lockedItem(attempt.getPlanItem().getId());
        checkVersion(item, command.version());
        requireCurrent(attempt, item);
        if (actor.getDepartment() == null || !actor.getDepartment().getId().equals(item.getDepartmentAtPlan().getId()))
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_VIOLATION", "Item belongs to another department");
        if (acceptances.findByExecution_IdAndAcceptanceType(executionId, AcceptanceType.HANDOVER_ACCEPTANCE).isPresent())
            conflict("HANDOVER_ACCEPTANCE_EXISTS", "Handover already recorded");
        if (item.getStatus() != PlanItemStatus.AWAITING_HANDOVER)
            conflict("HANDOVER_NOT_ALLOWED", "Item is not awaiting handover");
        AcceptanceRecord technical = acceptances.findByExecution_IdAndAcceptanceType(executionId,
                AcceptanceType.TECHNICAL_ACCEPTANCE).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.CONFLICT, "TECHNICAL_ACCEPTANCE_REQUIRED", "Current attempt needs technical PASS"));
        if (technical.getResult() != AcceptanceResult.PASS)
            conflict("TECHNICAL_ACCEPTANCE_REQUIRED", "Current attempt needs technical PASS");
        if (command.result() == AcceptanceResult.PASS && command.repairRequired())
            conflict("INVALID_ACCEPTANCE_OUTCOME", "PASS cannot require repair");
        UserAccount vtytSigner = null;
        if (command.result() == AcceptanceResult.PASS) {
            if (vtytAuthorization == null || vtytAuthorization.isBlank())
                throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "HANDOVER_SIGNER_REQUIRED",
                        "Authenticated VTYT confirmation is required");
            vtytSigner = coSigner.authenticate(vtytAuthorization);
            if (vtytSigner == null)
                throw new BusinessRuleException(HttpStatus.FORBIDDEN, "HANDOVER_SIGNER_INVALID",
                        "VTYT confirmation is invalid");
        }
        String conclusion = required(command.conclusion(), "CONCLUSION_REQUIRED");
        OffsetDateTime at = now();
        AcceptanceRecord record = acceptance(attempt, actor, AcceptanceType.HANDOVER_ACCEPTANCE,
                command.result(), conclusion, at);
        record.setDepartmentConfirmedByUser(actor);
        record.setDepartmentConfirmedAt(at);
        if (vtytSigner != null) {
            record.setRecordedByUser(vtytSigner);
            record.setVtytConfirmedByUser(vtytSigner);
            record.setVtytConfirmedAt(at);
        }
        acceptances.save(record);
        PlanItemStatus next = command.result() == AcceptanceResult.PASS
                ? PlanItemStatus.COMPLETED
                : command.repairRequired() ? PlanItemStatus.REPAIR_REQUIRED : PlanItemStatus.REWORK_REQUIRED;
        transition(item, actor, next, "HANDOVER_ACCEPTANCE", command.result() == AcceptanceResult.FAIL ? conclusion : null, at);
        notifications.notifyRole(UserRole.PHONG_VTYT,null,actor,next==PlanItemStatus.COMPLETED?"HANDOVER_COMPLETED":"HANDOVER_REWORK",next==PlanItemStatus.COMPLETED?"Khoa đã xác nhận bàn giao":"Thiết bị cần xử lý sau bàn giao",item.getEquipment().getEquipmentCode()+" — "+conclusion,"/plans/"+item.getPlan().getId()+"/items/"+item.getId()+"/execution");
        if (next == PlanItemStatus.COMPLETED || next == PlanItemStatus.REPAIR_REQUIRED) reportReady(item, actor, at);
        entityManager.flush();
        return acceptanceResponse(record, item);
    }

    private MaintenancePlanItem lockedItem(Long id) {
        if (id == null || id <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Item ID must be positive");
        MaintenancePlanItem item = items.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "PLAN_ITEM_NOT_FOUND", "Plan item not found"));
        entityManager.lock(item.getPlan(), LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(item.getPlan());
        entityManager.refresh(item);
        entityManager.lock(item, LockModeType.PESSIMISTIC_WRITE);
        return item;
    }

    private MaintenanceExecution findExecution(Long id) {
        if (id == null || id <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Execution ID must be positive");
        return executions.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "EXECUTION_NOT_FOUND", "Execution not found"));
    }

    private void requireCurrent(MaintenanceExecution attempt, MaintenancePlanItem item) {
        MaintenanceExecution latest = executions.findFirstByPlanItem_IdOrderByAttemptNoDesc(item.getId()).orElseThrow();
        if (!latest.getId().equals(attempt.getId()))
            conflict("EXECUTION_NOT_CURRENT", "Execution is not the current attempt");
        if (item.getPlan().getStatus() != PlanStatus.IN_PROGRESS)
            conflict("PLAN_NOT_EXECUTABLE", "Plan is not in progress");
    }

    private ServiceProvider routeProvider(MaintenancePlanItem item) {
        MaintenanceCoverage coverage = item.getCoverage();
        ServiceProvider assigned = item.getAssignedProvider();
        if(assigned==null || !Boolean.TRUE.equals(assigned.getActive()))conflict("ROUTING_EVIDENCE_MISSING","Active provider required");
        LocalDate date=item.getPlannedDate()==null?item.getPlan().getPeriodStart():item.getPlannedDate();
        if(item.getAssignmentRoute()==AssignmentRoute.UNDER_CONTRACT && PlanningDecisionService.validFree(coverage,item.getEquipment().getId(),date) && coverage.getProvider().getId().equals(assigned.getId()))return assigned;
        if(item.getAssignmentRoute()==AssignmentRoute.EXTERNAL_APPROVED) {
            List<ApprovalRequest> rounds = requests.findByPlanItem_IdOrderBySubmittedAtAscIdAsc(item.getId()).stream()
                    .filter(r -> r.getRequestType() == ApprovalRequestType.VENDOR_SELECTION
                            && r.getStatus() != ApprovalRequestStatus.DRAFT && r.getStatus()!=ApprovalRequestStatus.CANCELLED).toList();
            if (!rounds.isEmpty()) {
                ApprovalRequest latest = rounds.get(rounds.size() - 1);
                ApprovalAction action = actions.findByRequest_Id(latest.getId()).orElse(null);
                if (latest.getStatus() == ApprovalRequestStatus.DECIDED && action != null
                        && action.getOutcome() == ApprovalOutcome.APPROVE
                        && latest.getProposedProvider() != null
                        && latest.getProposedProvider().getId().equals(assigned.getId())) return assigned;
            }
        }
        conflict("INVALID_PROVIDER_ROUTE", "Provider route does not match its approved evidence");
        return assigned;
    }

    private AcceptanceRecord acceptance(MaintenanceExecution attempt, UserAccount actor,
            AcceptanceType type, AcceptanceResult result, String conclusion, OffsetDateTime at) {
        AcceptanceRecord record = new AcceptanceRecord();
        record.setExecution(attempt);
        record.setAcceptanceType(type);
        record.setResult(result);
        record.setObservedAt(at);
        record.setConclusion(conclusion);
        record.setRecordedByUser(actor);
        return record;
    }

    private void transition(MaintenancePlanItem item, UserAccount actor, PlanItemStatus next,
            String action, String reason, OffsetDateTime at) {
        PlanItemStatus old = item.getStatus();
        item.setStatus(next);
        history.itemTransition(item, actor, old.name(), next.name(), action, reason, at);
    }

    private void reportReady(MaintenancePlanItem item, UserAccount actor, OffsetDateTime at) {
        MaintenancePlan plan = item.getPlan();
        if (plan.getStatus() != PlanStatus.IN_PROGRESS) return;
        if (items.findAllByPlan_Id(plan.getId()).stream().allMatch(i ->
                i.getStatus() == PlanItemStatus.COMPLETED || i.getStatus() == PlanItemStatus.REPAIR_REQUIRED)) {
            plan.setStatus(PlanStatus.AWAITING_REPORT);
            history.plan(plan, actor, "IN_PROGRESS", "AWAITING_REPORT", "ALL_ITEMS_FINAL", null, at);
        }
    }

    private UserAccount requireRole(UserRole role) {
        var principal = currentUser.get();
        if (principal.role() != role)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Workflow role required");
        return users.getReferenceById(principal.id());
    }

    private void checkVersion(MaintenancePlanItem item, Integer expected) {
        if (expected == null || !item.getVersion().equals(expected))
            conflict("OPTIMISTIC_LOCK_CONFLICT", "Plan item has changed; reload before retrying");
    }

    private ExecutionWorkflowResponse executionResponse(MaintenanceExecution attempt, MaintenancePlanItem item) {
        return new ExecutionWorkflowResponse(attempt.getId(), item.getId(), attempt.getAttemptNo(),
                attempt.getProvider().getId(), item.getStatus(), item.getVersion(),
                item.getPlan().getStatus(), item.getPlan().getVersion());
    }

    private AcceptanceWorkflowResponse acceptanceResponse(AcceptanceRecord record, MaintenancePlanItem item) {
        return new AcceptanceWorkflowResponse(record.getId(), record.getExecution().getId(),
                record.getAcceptanceType(), record.getResult(), item.getStatus(), item.getVersion(),
                item.getPlan().getStatus(), item.getPlan().getVersion());
    }

    private String required(String value, String code) {
        if (blank(value))
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, code, "Required text is missing");
        return value.trim();
    }

    private String optional(String value) { return value == null ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    private void conflict(String code, String message) {
        throw new BusinessRuleException(HttpStatus.CONFLICT, code, message);
    }
}
