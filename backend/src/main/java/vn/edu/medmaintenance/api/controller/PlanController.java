package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.*;
import vn.edu.medmaintenance.api.exception.ResourceNotFoundException;
import vn.edu.medmaintenance.api.mapper.PlanMapper;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanItemRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.service.BusinessRuleException;
import vn.edu.medmaintenance.service.PlanningService;

@RestController
@RequestMapping("/api/plans")
public class PlanController {
    private static final Set<String> PLAN_SORT = Set.of("createdAt", "periodStart", "status", "id");
    private static final Set<String> ITEM_SORT = Set.of("id", "plannedDate", "status");
    private final MaintenancePlanRepository plans;
    private final MaintenancePlanItemRepository items;
    private final PlanningService planning;
    private final CurrentUser currentUser;
    private final vn.edu.medmaintenance.persistence.repository.StatusHistoryRepository histories;
    private final vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository requests;

    public PlanController(MaintenancePlanRepository plans, MaintenancePlanItemRepository items,
            PlanningService planning, CurrentUser currentUser,vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository requests,
            vn.edu.medmaintenance.persistence.repository.StatusHistoryRepository histories) {
        this.plans = plans;
        this.items = items;
        this.planning = planning;
        this.currentUser = currentUser;this.requests=requests;this.histories=histories;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanCommandResponse create(@Valid @RequestBody CreatePlanRequest command) {
        return planning.create(command);
    }

    @PatchMapping("/{id}")
    public PlanCommandResponse edit(@PathVariable Long id, @Valid @RequestBody EditPlanRequest command) {
        return planning.edit(id, command);
    }

    @PostMapping("/{id}/submit")
    public PlanCommandResponse submit(@PathVariable Long id, @Valid @RequestBody SubmitPlanRequest command) {
        return planning.submit(id, command);
    }

    @GetMapping
    public PageResponse<MaintenancePlanResponse> list(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) PlanStatus status) {
        Pageable pageable = PageRequests.create(query, PLAN_SORT, "createdAt", Sort.Direction.DESC);
        var viewer = currentUser.get();
        var result = viewer.role() == UserRole.KHOA_PHONG
                ? plans.findVisibleForDepartment(viewer.departmentId(), status, pageable)
                : status == null ? plans.findAllBy(pageable) : plans.findByStatus(status, pageable);
        return PageResponse.from(result, PlanMapper::toResponse);
    }

    @GetMapping("/{id}")
    public MaintenancePlanResponse detail(@PathVariable Long id) {
        PageRequests.requirePositive(id, "id");
        requireVisiblePlan(id);
        return plans.findWithCreatorById(id).map(plan -> PlanMapper.toResponse(plan,
                histories.existsByPlanItem_Plan_IdAndPlanItem_StatusAndAction(id,
                        vn.edu.medmaintenance.persistence.enums.PlanItemStatus.WAITING_VENDOR_APPROVAL,
                        "ACTIVATE_PREPARED_VENDOR")))
                .orElseThrow(() -> new ResourceNotFoundException("Plan"));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    @GetMapping("/{planId}/items")
    public PageResponse<MaintenancePlanItemResponse> items(@PathVariable Long planId,
            @Valid @ModelAttribute PageQuery query) {
        PageRequests.requirePositive(planId, "planId");
        requireVisiblePlan(planId);
        Pageable pageable = PageRequests.create(query, ITEM_SORT, "id", Sort.Direction.ASC);
        var viewer = currentUser.get();
        var result = viewer.role() == UserRole.KHOA_PHONG
                ? items.findByPlan_IdAndDepartmentAtPlan_Id(planId, viewer.departmentId(), pageable)
                : items.findByPlan_Id(planId, pageable);
        var proposals=new java.util.HashMap<Long,vn.edu.medmaintenance.persistence.entity.ApprovalRequest>();
        for(var request:requests.findProposalsForPlan(planId))proposals.put(request.getPlanItem().getId(),request);
        return PageResponse.from(result, item -> PlanMapper.toItemResponse(item, planId,proposals.get(item.getId())));
    }

    private void requireVisiblePlan(Long planId) {
        if (!plans.existsById(planId)) throw new ResourceNotFoundException("Plan");
        var viewer = currentUser.get();
        if (viewer.role() == UserRole.KHOA_PHONG
                && (viewer.departmentId() == null
                    || !plans.visibleToDepartment(planId, viewer.departmentId())))
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_VIOLATION",
                    "Plan is outside department scope");
    }
}
