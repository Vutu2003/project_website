package vn.edu.medmaintenance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.CreateVendorProposalRequest;
import vn.edu.medmaintenance.api.dto.request.DecidePlanRequest;
import vn.edu.medmaintenance.api.dto.request.RouteItemRequest;
import vn.edu.medmaintenance.api.dto.request.SubmitVendorProposalRequest;
import vn.edu.medmaintenance.api.dto.response.ItemWorkflowResponse;
import vn.edu.medmaintenance.persistence.entity.ApprovalAction;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.enums.AssignmentRoute;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.ApprovalActionRepository;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanItemRepository;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class MaintenanceAssignmentService {
    private final MaintenancePlanItemRepository items;
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final WorkflowHistory history;
    private final EntityManager entityManager;
    private final PlanningDecisionService decisions;private final NotificationService notifications;

    public MaintenanceAssignmentService(MaintenancePlanItemRepository items,
            ApprovalRequestRepository requests, ApprovalActionRepository actions,
            UserAccountRepository users, CurrentUser currentUser, WorkflowHistory history,
            EntityManager entityManager,PlanningDecisionService decisions,NotificationService notifications) {
        this.items = items;
        this.requests = requests;
        this.actions = actions;
        this.users = users;
        this.currentUser = currentUser;
        this.history = history;
        this.entityManager = entityManager;this.decisions=decisions;this.notifications=notifications;
    }

    @Transactional
    public ItemWorkflowResponse route(Long itemId, RouteItemRequest command) {
        requireRole(UserRole.PHONG_VTYT);
        conflict("PLANNING_WORKFLOW_REQUIRED","Chọn hình thức trong biểu mẫu tạo hoặc hiệu chỉnh kế hoạch.");return null;
    }
    @Transactional public ItemWorkflowResponse createVendorDraft(Long itemId,CreateVendorProposalRequest command){requireRole(UserRole.PHONG_VTYT);conflict("PLANNING_WORKFLOW_REQUIRED","Chuẩn bị đề xuất trong biểu mẫu kế hoạch.");return null;}
    @Transactional public ItemWorkflowResponse submitVendorDraft(Long requestId,SubmitVendorProposalRequest command){requireRole(UserRole.PHONG_VTYT);conflict("PLANNING_WORKFLOW_REQUIRED","Đề xuất tự chuyển phê duyệt khi kế hoạch được duyệt.");return null;}

    @Transactional
    public ItemWorkflowResponse decideVendor(Long requestId, DecidePlanRequest command) {
        UserAccount actor = requireRole(UserRole.BAN_GIAM_DOC);
        ApprovalRequest request = findVendorRequest(requestId);
        MaintenancePlanItem item = request.getPlanItem();
        checkVersion(item, command.version());
        if (request.getStatus() != ApprovalRequestStatus.PENDING || actions.findByRequest_Id(requestId).isPresent())
            conflict("APPROVAL_REQUEST_NOT_PENDING", "Vendor request has already been decided");
        if(item.getPlan().getStatus()!=PlanStatus.APPROVED && item.getPlan().getStatus()!=PlanStatus.IN_PROGRESS)
            conflict("PLAN_NOT_APPROVED","Kế hoạch chưa được phê duyệt.");
        if(command.outcome()==ApprovalOutcome.REVISION_REQUIRED && item.getPlan().getStatus()!=PlanStatus.APPROVED)
            conflict("VENDOR_REVISION_AFTER_EXECUTION","Không thể trả toàn bộ kế hoạch về hiệu chỉnh khi đã bắt đầu thực hiện. Cần xử lý kế hoạch đang thực hiện theo quy trình riêng.");
        requireStatus(item, PlanItemStatus.WAITING_VENDOR_APPROVAL);
        entityManager.lock(item.getPlan(),LockModeType.OPTIMISTIC);
        ServiceProvider provider = request.getProposedProvider();
        if (provider == null) conflict("PROVIDER_REQUIRED", "Vendor request has no proposed provider");
        requireActive(provider);
        String comment = trim(command.comment());
        if (command.outcome() == ApprovalOutcome.REVISION_REQUIRED && (comment == null || comment.isBlank()))
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "REVISION_COMMENT_REQUIRED",
                    "A revision reason is required");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ApprovalAction action = new ApprovalAction();
        action.setRequest(request);
        action.setActorUser(actor);
        action.setOutcome(command.outcome());
        action.setComment(comment);
        action.setActionAt(now);
        actions.save(action);
        request.setStatus(ApprovalRequestStatus.DECIDED);
        request.setResolvedAt(now);
        if (command.outcome() == ApprovalOutcome.APPROVE) {
            item.setAssignedProvider(provider);
            item.setAssignmentRoute(AssignmentRoute.EXTERNAL_APPROVED);
            item.setStatus(PlanItemStatus.ASSIGNED_EXTERNAL);
            history.itemTransition(item, actor, "WAITING_VENDOR_APPROVAL", "ASSIGNED_EXTERNAL",
                    "RECORD_VENDOR_APPROVAL", null, now);
        } else {
            decisions.returnForVendorRevision(item.getPlan(),actor,comment,now);
        }
        notifications.notifyRole(UserRole.PHONG_VTYT,null,actor,command.outcome()==ApprovalOutcome.APPROVE?"VENDOR_APPROVED":"VENDOR_REVISION",command.outcome()==ApprovalOutcome.APPROVE?"Đơn vị bảo trì đã được phê duyệt":"Đề xuất đơn vị cần điều chỉnh",item.getEquipment().getEquipmentCode()+(comment==null?"":" — "+comment),"/plans/"+item.getPlan().getId()+(command.outcome()==ApprovalOutcome.REVISION_REQUIRED?"/edit":""));
        entityManager.flush();
        return response(item, request, action, command.outcome());
    }

    private MaintenancePlanItem findItem(Long id) {
        if (id == null || id <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Item ID must be positive");
        return items.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "PLAN_ITEM_NOT_FOUND", "Plan item not found"));
    }

    private ApprovalRequest findVendorRequest(Long id) {
        if (id == null || id <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Request ID must be positive");
        ApprovalRequest request = requests.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND", "Approval request not found"));
        if (request.getRequestType() != ApprovalRequestType.VENDOR_SELECTION || request.getPlanItem() == null)
            conflict("APPROVAL_REQUEST_WRONG_TYPE", "Request is not a vendor proposal");
        return request;
    }

    private UserAccount requireRole(UserRole role) {
        var principal = currentUser.get();
        if (principal.role() != role)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Workflow role required");
        return users.getReferenceById(principal.id());
    }

    private void requireStatus(MaintenancePlanItem item, PlanItemStatus expected) {
        if (item.getStatus() != expected)
            conflict("PLAN_ITEM_STATE_CONFLICT", "Plan item is not in the required state");
    }

    private void checkVersion(MaintenancePlanItem item, Integer expected) {
        if (expected == null || !item.getVersion().equals(expected))
            conflict("OPTIMISTIC_LOCK_CONFLICT", "Plan item has changed; reload before retrying");
    }

    private void requireActive(ServiceProvider provider) {
        if (!Boolean.TRUE.equals(provider.getActive()))
            conflict("PROVIDER_INACTIVE", "Provider is inactive");
    }

    private String trim(String value) { return value == null ? null : value.trim(); }

    private void conflict(String code, String message) {
        throw new BusinessRuleException(HttpStatus.CONFLICT, code, message);
    }

    private ItemWorkflowResponse response(MaintenancePlanItem item, ApprovalRequest request,
            ApprovalAction action, ApprovalOutcome outcome) {
        return new ItemWorkflowResponse(item.getId(), item.getStatus(), item.getVersion(),
                item.getAssignmentRoute(), item.getAssignedProvider() == null ? null : item.getAssignedProvider().getId(),
                item.getCoverage() == null ? null : item.getCoverage().getId(),
                request == null ? null : request.getId(), request == null ? null : request.getStatus(),
                action == null ? null : action.getId(), outcome,
                item.getPlan().getId(), item.getPlan().getStatus(), item.getPlan().getVersion(),
                item.getAssignedProvider() == null ? null : item.getAssignedProvider().getName());
    }
}
