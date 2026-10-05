package vn.edu.medmaintenance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.CreatePlanRequest;
import vn.edu.medmaintenance.api.dto.request.EditPlanRequest;
import vn.edu.medmaintenance.api.dto.request.PlanItemInput;
import vn.edu.medmaintenance.api.dto.request.SubmitPlanRequest;
import vn.edu.medmaintenance.api.dto.response.PlanCommandResponse;
import vn.edu.medmaintenance.persistence.entity.Equipment;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;
import vn.edu.medmaintenance.persistence.repository.EquipmentRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanItemRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanRepository;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class PlanningService {
    private final MaintenancePlanRepository plans;
    private final MaintenancePlanItemRepository items;
    private final EquipmentRepository equipment;
    private final ApprovalRequestRepository requests;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final WorkflowHistory history;
    private final EntityManager entityManager;
    private final PlanningDecisionService decisions;
    private final NotificationService notifications;
    private final MaintenanceAutomationService automation;

    public PlanningService(MaintenancePlanRepository plans, MaintenancePlanItemRepository items,
            EquipmentRepository equipment, ApprovalRequestRepository requests, UserAccountRepository users,
            CurrentUser currentUser, WorkflowHistory history, EntityManager entityManager, PlanningDecisionService decisions, NotificationService notifications, MaintenanceAutomationService automation) {
        this.plans = plans;
        this.items = items;
        this.equipment = equipment;
        this.requests = requests;
        this.users = users;
        this.currentUser = currentUser;
        this.history = history;
        this.entityManager = entityManager;this.decisions=decisions;this.notifications=notifications;this.automation=automation;
    }

    @Transactional
    public PlanCommandResponse createQuarterly(int year, MaintenanceQuarter quarter, List<PlanItemInput> inputs) {
        // Transaction advisory lock makes the friendly duplicate check safe across concurrent requests.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
            .setParameter("key", (long)year * 4 + quarter.ordinal()).getSingleResult();
        Long count=((Number)entityManager.createNativeQuery("SELECT count(*) FROM maintenance_plan WHERE plan_year=:year AND plan_quarter=:quarter AND status NOT IN ('CLOSED','REPORTED')")
            .setParameter("year",year).setParameter("quarter",quarter.name()).getSingleResult()).longValue();
        if(count>0)conflict("DUPLICATE_QUARTER_PLAN",quarter.title(year)+" đã tồn tại.");
        return create(new CreatePlanRequest(quarter.title(year),quarter.start(year),quarter.end(year),inputs),year,quarter);
    }
    @Transactional
    public PlanCommandResponse create(CreatePlanRequest command) { return create(command,null,null); }
    private PlanCommandResponse create(CreatePlanRequest command,Integer year,MaintenanceQuarter quarter) {
        UserAccount actor = planner();
        validatePeriod(command.periodStart(), command.periodEnd());
        MaintenancePlan plan = new MaintenancePlan();
        plan.setPlanYear(year);plan.setPlanQuarter(quarter==null?null:quarter.name());
        plan.setTitle(command.title().trim());
        plan.setPeriodStart(command.periodStart());
        plan.setPeriodEnd(command.periodEnd());
        plan.setStatus(PlanStatus.DRAFT);
        plan.setCreatedByUser(actor);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        plan.setCreatedAt(now);
        plans.save(plan);
        history.plan(plan, actor, null, "DRAFT", "CREATE", null, now);
        Set<Long> seen = new HashSet<>();
        for (PlanItemInput input : command.items().stream().sorted(java.util.Comparator.comparing(PlanItemInput::equipmentId)).toList()) {
            if (!seen.add(input.equipmentId())) conflict("DUPLICATE_PLAN_EQUIPMENT", "Equipment appears more than once");
            addItem(plan, input, actor, now);
        }
        entityManager.flush();
        return response(plan, null);
    }

    @Transactional
    public PlanCommandResponse edit(Long planId, EditPlanRequest command) {
        UserAccount actor = planner();
        MaintenancePlan plan = findPlan(planId);
        checkVersion(plan, command.version());
        if (plan.getStatus() != PlanStatus.DRAFT && plan.getStatus() != PlanStatus.REVISION_REQUIRED)
            conflict("PLAN_NOT_EDITABLE", "Plan is not editable in its current state");
        if (command.removeEquipmentIds() != null && !command.removeEquipmentIds().isEmpty())
            conflict("PLAN_ITEM_RETENTION_CONFLICT", "Removing audited plan items is not supported by the frozen schema");
        if(plan.getPlanYear()!=null && (!plan.getTitle().equals(command.title()) || !plan.getPeriodStart().equals(command.periodStart()) || !plan.getPeriodEnd().equals(command.periodEnd())))
            conflict("FIXED_QUARTER_PERIOD","Tên và thời gian kế hoạch quý do hệ thống xác định.");
        validatePeriod(command.periodStart(), command.periodEnd());
        Map<Long, MaintenancePlanItem> existing = new HashMap<>();
        for (MaintenancePlanItem item : items.findAllByPlan_Id(planId)) {
            // A concurrent UC05 decision must invalidate edits based on an old item snapshot.
            entityManager.lock(item, LockModeType.OPTIMISTIC);
            existing.put(item.getEquipment().getId(), item);
        }
        plan.setPeriodStart(command.periodStart());
        plan.setPeriodEnd(command.periodEnd());
        if (command.items() != null) {
            Set<Long> seen = new HashSet<>();
            for (PlanItemInput input : command.items().stream().sorted(java.util.Comparator.comparing(PlanItemInput::equipmentId)).toList()) {
                if (!seen.add(input.equipmentId())) conflict("DUPLICATE_PLAN_EQUIPMENT", "Equipment appears more than once");
                validateDate(input.plannedDate(), command.periodStart(), command.periodEnd());
                MaintenancePlanItem item = existing.get(input.equipmentId());
                if(plan.getPlanYear()!=null && (item==null || !plan.getPeriodStart().equals(input.plannedDate())))
                    conflict("FIXED_QUARTER_EQUIPMENT","Thiết bị và ngày bảo trì của quý do hệ thống xác định.");
                if (item == null) {
                    addItem(plan, input, actor, OffsetDateTime.now(ZoneOffset.UTC));
                } else {
                    if (item.getStatus() != PlanItemStatus.PLANNED
                            && item.getStatus() != PlanItemStatus.UNDER_CONTRACT
                            && item.getStatus() != PlanItemStatus.PENDING_PROPOSAL)
                        conflict("PLAN_ITEM_NOT_EDITABLE", "Only planned items may be edited");
                    if(input.version()!=null && !input.version().equals(item.getVersion()))conflict("OPTIMISTIC_LOCK_CONFLICT","Hạng mục đã thay đổi.");
                    if(!Objects.equals(item.getPlannedDate(),input.plannedDate()))
                        history.itemTransition(item,actor,item.getStatus().name(),item.getStatus().name(),"UPDATE_PLANNING_DATE","old="+item.getPlannedDate()+"; new="+input.plannedDate(),OffsetDateTime.now(ZoneOffset.UTC));
                    item.setPlannedDate(input.plannedDate());
                    automation.snapshot(item);
                    decisions.apply(item,input,actor);
                }
            }
        }
        for (MaintenancePlanItem item : existing.values()) {
            LocalDate newDate = item.getPlannedDate();
            if (command.items() != null) for (PlanItemInput input : command.items())
                if (item.getEquipment().getId().equals(input.equipmentId())) newDate = input.plannedDate();
            validateDate(newDate, command.periodStart(), command.periodEnd());
        }
        boolean metadataChanged = !plan.getTitle().equals(command.title().trim())
                || !plan.getPeriodStart().equals(command.periodStart())
                || !plan.getPeriodEnd().equals(command.periodEnd());
        plan.setTitle(command.title().trim());
        plan.setPeriodStart(command.periodStart());
        plan.setPeriodEnd(command.periodEnd());
        boolean forcedVersion = plan.getStatus() == PlanStatus.DRAFT && !metadataChanged;
        if (plan.getStatus() == PlanStatus.REVISION_REQUIRED) {
            plan.setStatus(PlanStatus.DRAFT);
            history.plan(plan, actor, "REVISION_REQUIRED", "DRAFT", "SAVE_REVISION", null,
                    OffsetDateTime.now(ZoneOffset.UTC));
        } else {
            history.plan(plan, actor, "DRAFT", "DRAFT", "EDIT_PLAN", null,
                    OffsetDateTime.now(ZoneOffset.UTC));
            if (forcedVersion) entityManager.lock(plan, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        entityManager.flush();
        return forcedVersion
                ? new PlanCommandResponse(plan.getId(), plan.getStatus(), plan.getVersion() + 1, null)
                : response(plan, null);
    }

    @Transactional
    public PlanCommandResponse submit(Long planId, SubmitPlanRequest command) {
        UserAccount actor = planner();
        MaintenancePlan plan = findPlan(planId);
        checkVersion(plan, command.version());
        if (requests.existsByPlan_IdAndRequestTypeAndStatus(planId, ApprovalRequestType.PLAN_APPROVAL,
                ApprovalRequestStatus.PENDING))
            conflict("PLAN_ALREADY_PENDING_APPROVAL", "A pending plan approval already exists");
        if (plan.getStatus() != PlanStatus.DRAFT)
            conflict("PLAN_NOT_SUBMITTABLE", "Only a draft plan may be submitted");
        List<MaintenancePlanItem> planItems = items.findAllByPlan_Id(planId);
        if (planItems.isEmpty()) conflict("PLAN_NOT_SUBMITTABLE", "Plan needs at least one item");
        for (MaintenancePlanItem item : planItems) {
            if (item.getStatus() != PlanItemStatus.PLANNED
                    && item.getStatus() != PlanItemStatus.UNDER_CONTRACT
                    && item.getStatus() != PlanItemStatus.PENDING_PROPOSAL)
                conflict("PLAN_NOT_SUBMITTABLE", "Plan has an item outside planning state");
            validateDate(item.getPlannedDate(), plan.getPeriodStart(), plan.getPeriodEnd());
            decisions.validateComplete(item);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        plan.setStatus(PlanStatus.SUBMITTED);
        ApprovalRequest request = new ApprovalRequest();
        request.setRequestType(ApprovalRequestType.PLAN_APPROVAL);
        request.setPlan(plan);
        request.setStatus(ApprovalRequestStatus.PENDING);
        request.setCreatedByUser(actor);
        request.setSubmittedAt(now);
        requests.save(request);
        history.plan(plan, actor, "DRAFT", "SUBMITTED", "SUBMIT_PLAN", null, now);
        notifications.notifyRole(UserRole.BAN_GIAM_DOC,null,actor,"PLAN_SUBMITTED","Kế hoạch chờ phê duyệt",plan.getTitle(),"/approvals/"+request.getId());
        entityManager.flush();
        return response(plan, request.getId());
    }

    private void addItem(MaintenancePlan plan, PlanItemInput input, UserAccount actor, OffsetDateTime now) {
        validateDate(input.plannedDate(), plan.getPeriodStart(), plan.getPeriodEnd());
        Equipment device = equipment.findWithDepartmentById(input.equipmentId())
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                        "EQUIPMENT_NOT_FOUND", "Equipment not found"));
        if (!Boolean.TRUE.equals(device.getActive()))
            conflict("EQUIPMENT_INACTIVE", "Equipment is not active");
        entityManager.lock(device, LockModeType.PESSIMISTIC_WRITE);
        boolean duplicate=items.findByEquipment_IdIn(List.of(device.getId())).stream().anyMatch(i ->
            !i.getPlan().getId().equals(plan.getId()) &&
            !Set.of(PlanStatus.CLOSED,PlanStatus.REPORTED,PlanStatus.AWAITING_REPORT).contains(i.getPlan().getStatus()) &&
            i.getStatus()!=PlanItemStatus.COMPLETED && i.getStatus()!=PlanItemStatus.REPAIR_REQUIRED);
        if(duplicate && plan.getPlanYear()==null)conflict("EQUIPMENT_IN_OPEN_PLAN","Thiết bị "+device.getEquipmentCode()+" đã có trong kế hoạch đang mở.");
        MaintenancePlanItem item = new MaintenancePlanItem();
        item.setPlan(plan);
        item.setEquipment(device);
        item.setDepartmentAtPlan(device.getDepartment());
        item.setPlannedDate(input.plannedDate());
        item.setStatus(PlanItemStatus.PLANNED);
        items.save(item);
        history.item(item, actor, "CREATE", now);
        automation.snapshot(item);
        decisions.apply(item,input,actor);
    }

    private UserAccount planner() {
        var principal = currentUser.get();
        if (principal.role() != UserRole.PHONG_VTYT)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Planning role required");
        return users.getReferenceById(principal.id());
    }

    private MaintenancePlan findPlan(Long id) {
        if (id == null || id <= 0) throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER", "Plan ID must be positive");
        return plans.findById(id).orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                "PLAN_NOT_FOUND", "Plan not found"));
    }

    private void checkVersion(MaintenancePlan plan, Integer expected) {
        if (!plan.getVersion().equals(expected))
            conflict("OPTIMISTIC_LOCK_CONFLICT", "Plan has changed; reload before retrying");
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                "INVALID_PLAN_PERIOD", "Plan end must not precede start");
    }

    private void validateDate(LocalDate date, LocalDate start, LocalDate end) {
        if (date != null && (date.isBefore(start) || date.isAfter(end)))
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                    "INVALID_PLANNED_DATE", "Planned date must fall within plan period");
    }

    private void conflict(String code, String message) {
        throw new BusinessRuleException(HttpStatus.CONFLICT, code, message);
    }

    private PlanCommandResponse response(MaintenancePlan plan, Long requestId) {
        return new PlanCommandResponse(plan.getId(), plan.getStatus(), plan.getVersion(), requestId);
    }
}
