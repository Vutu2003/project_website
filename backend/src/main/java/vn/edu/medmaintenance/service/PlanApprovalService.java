package vn.edu.medmaintenance.service;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.DecidePlanRequest;
import vn.edu.medmaintenance.api.dto.response.ApprovalDecisionResponse;
import vn.edu.medmaintenance.persistence.entity.ApprovalAction;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.ApprovalActionRepository;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class PlanApprovalService {
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final WorkflowHistory history;
    private final EntityManager entityManager;
    private final PlanningDecisionService decisions; private final NotificationService notifications;

    public PlanApprovalService(ApprovalRequestRepository requests, ApprovalActionRepository actions,
            UserAccountRepository users, CurrentUser currentUser, WorkflowHistory history,
            EntityManager entityManager, PlanningDecisionService decisions,NotificationService notifications) {
        this.requests = requests;
        this.actions = actions;
        this.users = users;
        this.currentUser = currentUser;
        this.history = history;
        this.entityManager = entityManager;this.decisions=decisions;this.notifications=notifications;
    }

    @Transactional
    public ApprovalDecisionResponse decide(Long requestId, DecidePlanRequest command) {
        var principal = currentUser.get();
        if (principal.role() != UserRole.BAN_GIAM_DOC)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Director role required");
        if (requestId == null || requestId <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Request ID must be positive");
        ApprovalRequest request = requests.findById(requestId).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND", "Approval request not found"));
        if (request.getRequestType() != ApprovalRequestType.PLAN_APPROVAL || request.getPlan() == null)
            throw new BusinessRuleException(HttpStatus.CONFLICT, "APPROVAL_REQUEST_WRONG_TYPE",
                    "Request does not target a plan");
        MaintenancePlan plan = request.getPlan();
        entityManager.lock(plan, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(plan);
        entityManager.refresh(request);
        if (!plan.getVersion().equals(command.version()))
            throw new BusinessRuleException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT",
                    "Plan has changed; reload before retrying");
        if (request.getStatus() != ApprovalRequestStatus.PENDING || actions.findByRequest_Id(requestId).isPresent())
            throw new BusinessRuleException(HttpStatus.CONFLICT, "APPROVAL_REQUEST_NOT_PENDING",
                    "Approval request has already been decided");
        if (plan.getStatus() != PlanStatus.SUBMITTED)
            throw new BusinessRuleException(HttpStatus.CONFLICT, "PLAN_STATE_CONFLICT",
                    "Plan is not awaiting approval");
        String comment = command.comment() == null ? null : command.comment().trim();
        if (command.outcome() == ApprovalOutcome.REVISION_REQUIRED && (comment == null || comment.isBlank()))
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "REVISION_COMMENT_REQUIRED",
                    "A revision reason is required");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ApprovalAction action = new ApprovalAction();
        action.setRequest(request);
        action.setActorUser(users.getReferenceById(principal.id()));
        action.setOutcome(command.outcome());
        action.setComment(comment);
        action.setActionAt(now);
        actions.save(action);
        request.setStatus(ApprovalRequestStatus.DECIDED);
        request.setResolvedAt(now);
        PlanStatus next = command.outcome() == ApprovalOutcome.APPROVE
                ? PlanStatus.APPROVED : PlanStatus.REVISION_REQUIRED;
        plan.setStatus(next);
        history.plan(plan, action.getActorUser(), "SUBMITTED", next.name(),
                next == PlanStatus.APPROVED ? "RECORD_APPROVAL" : "RECORD_REVISION",
                next == PlanStatus.REVISION_REQUIRED ? comment : null, now);
        if(next==PlanStatus.APPROVED)decisions.approvePreparedProviders(plan,action);
        notifications.notifyRole(UserRole.PHONG_VTYT,null,action.getActorUser(),next==PlanStatus.APPROVED?"PLAN_APPROVED":"PLAN_REVISION",next==PlanStatus.APPROVED?"Kế hoạch đã được phê duyệt":"Kế hoạch cần hiệu chỉnh",plan.getTitle()+(comment==null?"":" — "+comment),"/plans/"+plan.getId()+(next==PlanStatus.REVISION_REQUIRED?"/edit":""));
        entityManager.flush();
        return new ApprovalDecisionResponse(requestId, action.getId(), plan.getId(),
                command.outcome(), next, plan.getVersion());
    }
}
