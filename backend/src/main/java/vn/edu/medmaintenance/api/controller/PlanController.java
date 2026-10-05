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
    @org.springframework.beans.factory.annotation.Value("${app.planning.legacy-fixture-creation:false}") private boolean legacyFixtureCreation;
    private static final Set<String> PLAN_SORT = Set.of("createdAt", "periodStart", "status", "id");
    private static final Set<String> ITEM_SORT = Set.of("id", "plannedDate", "status");
    private final MaintenancePlanRepository plans;
    private final MaintenancePlanItemRepository items;
    private final PlanningService planning;
    private final vn.edu.medmaintenance.service.PlanDeletionService deletion;
    private final CurrentUser currentUser;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository requests;

    public PlanController(MaintenancePlanRepository plans, MaintenancePlanItemRepository items,
            PlanningService planning, CurrentUser currentUser,vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository requests,
            org.springframework.jdbc.core.JdbcTemplate jdbc, vn.edu.medmaintenance.service.PlanDeletionService deletion) {
        this.plans = plans;this.jdbc=jdbc;this.deletion=deletion;
        this.items = items;
        this.planning = planning;
        this.currentUser = currentUser;this.requests=requests;
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id,@RequestParam int version){deletion.delete(id,version);}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanCommandResponse create(@Valid @RequestBody CreatePlanRequest command) {
        if(!legacyFixtureCreation) throw new BusinessRuleException(HttpStatus.BAD_REQUEST,"QUARTER_REQUIRED","Tạo kế hoạch bằng năm và quý tại /api/maintenance-plans.");
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
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) vn.edu.medmaintenance.persistence.enums.PlanItemStatus itemStatus) {
        Pageable pageable = PageRequests.create(query, PLAN_SORT, "createdAt", Sort.Direction.DESC);
        var viewer = currentUser.get();
        if (viewer.role() == UserRole.KHOA_PHONG && viewer.departmentId() == null)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_VIOLATION",
                    "Department membership required");
        var result = itemStatus != null
                ? plans.findWithItemsInStatus(viewer.role() == UserRole.KHOA_PHONG
                        ? viewer.departmentId() : null, status, itemStatus, pageable)
                : viewer.role() == UserRole.KHOA_PHONG
                ? plans.findVisibleForDepartment(viewer.departmentId(), status, pageable)
                : status == null ? plans.findAllBy(pageable) : plans.findByStatus(status, pageable);
        var counts=new java.util.HashMap<Long,Long>();
        if(!result.isEmpty())jdbc.query("SELECT plan_id,count(*) n FROM maintenance_plan_item WHERE plan_id IN ("+String.join(",", java.util.Collections.nCopies(result.getNumberOfElements(),"?"))+") GROUP BY plan_id",(org.springframework.jdbc.core.RowCallbackHandler) rs->counts.put(rs.getLong(1),rs.getLong(2)),result.getContent().stream().map(p->(Object)p.getId()).toArray());
        return PageResponse.from(result, p -> withCount(PlanMapper.toResponse(p),counts.getOrDefault(p.getId(),0L)));
    }

    @GetMapping("/{id}")
    public MaintenancePlanResponse detail(@PathVariable Long id) {
        PageRequests.requirePositive(id, "id");
        requireVisiblePlan(id);
        return plans.findWithCreatorById(id).map(plan -> PlanMapper.toResponse(plan,
                items.existsByPlan_IdAndStatusIn(id, java.util.List.of(
                        vn.edu.medmaintenance.persistence.enums.PlanItemStatus.PLANNED,
                        vn.edu.medmaintenance.persistence.enums.PlanItemStatus.PENDING_PROPOSAL,
                        vn.edu.medmaintenance.persistence.enums.PlanItemStatus.WAITING_VENDOR_APPROVAL))))
                .orElseThrow(() -> new ResourceNotFoundException("Plan"));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    @GetMapping("/{planId}/items")
    public PageResponse<MaintenancePlanItemResponse> items(@PathVariable Long planId,
            @Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) vn.edu.medmaintenance.persistence.enums.PlanItemStatus status) {
        PageRequests.requirePositive(planId, "planId");
        requireVisiblePlan(planId);
        Pageable pageable = PageRequests.create(query, ITEM_SORT, "id", Sort.Direction.ASC);
        var viewer = currentUser.get();
        var result = status != null
                ? items.findVisibleInStatus(planId, viewer.role() == UserRole.KHOA_PHONG
                        ? viewer.departmentId() : null, status, pageable)
                : viewer.role() == UserRole.KHOA_PHONG
                ? items.findByPlan_IdAndDepartmentAtPlan_Id(planId, viewer.departmentId(), pageable)
                : items.findByPlan_Id(planId, pageable);
        var proposals=new java.util.HashMap<Long,vn.edu.medmaintenance.persistence.entity.ApprovalRequest>();
        for(var request:requests.findProposalsForPlan(planId))proposals.put(request.getPlanItem().getId(),request);
        return PageResponse.from(result, item -> PlanMapper.toItemResponse(item, planId,proposals.get(item.getId())));
    }

    private MaintenancePlanResponse withCount(MaintenancePlanResponse p,Long count){
        return new MaintenancePlanResponse(p.id(),p.title(),p.periodStart(),p.periodEnd(),p.status(),p.createdAt(),p.createdByUserId(),p.createdByName(),p.version(),p.pendingVendorApproval(),p.planYear(),p.planQuarter(),count);
    }
    @GetMapping("/{planId}/items/all")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public java.util.List<MaintenancePlanItemResponse> allItems(@PathVariable Long planId){
        requireVisiblePlan(planId);
        if(currentUser.get().role()==UserRole.KHOA_PHONG)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"BUSINESS_ACCESS_DENIED","Use department-scoped items");
        var proposals=new java.util.HashMap<Long,vn.edu.medmaintenance.persistence.entity.ApprovalRequest>();
        for(var request:requests.findProposalsForPlan(planId))proposals.put(request.getPlanItem().getId(),request);
        return items.findReportItems(planId).stream().map(i->PlanMapper.toItemResponse(i,planId,proposals.get(i.getId()))).toList();
    }
    @GetMapping("/{planId}/review-comments")
    public java.util.List<java.util.Map<String,Object>> comments(@PathVariable Long planId){
        requireVisiblePlan(planId);
        var viewer=currentUser.get();
        if(viewer.role()==UserRole.KHOA_PHONG)throw new BusinessRuleException(HttpStatus.FORBIDDEN,"BUSINESS_ACCESS_DENIED","Ý kiến duyệt dành cho VTYT và BGĐ");
        return jdbc.queryForList("""
          SELECT a.id,a.request_id,a.outcome,a.comment,a.action_at,u.display_name reviewer,
          r.request_type FROM approval_action a JOIN approval_request r ON r.id=a.request_id
          JOIN user_account u ON u.id=a.actor_user_id LEFT JOIN maintenance_plan_item i ON i.id=r.plan_item_id
          WHERE (r.plan_id=? OR i.plan_id=?) AND a.outcome='REVISION_REQUIRED' ORDER BY a.action_at DESC,a.id DESC
          """,planId,planId);
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
