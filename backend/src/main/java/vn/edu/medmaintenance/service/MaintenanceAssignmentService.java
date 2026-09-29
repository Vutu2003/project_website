package vn.edu.medmaintenance.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
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
import vn.edu.medmaintenance.persistence.entity.MaintenanceCoverage;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.enums.AssignmentRoute;
import vn.edu.medmaintenance.persistence.enums.CoverageClassification;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.ApprovalActionRepository;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenanceCoverageRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanItemRepository;
import vn.edu.medmaintenance.persistence.repository.ServiceProviderRepository;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class MaintenanceAssignmentService {
    private final MaintenancePlanItemRepository items;
    private final MaintenanceCoverageRepository coverages;
    private final ServiceProviderRepository providers;
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final UserAccountRepository users;
    private final CurrentUser currentUser;
    private final WorkflowHistory history;
    private final EntityManager entityManager;

    public MaintenanceAssignmentService(MaintenancePlanItemRepository items,
            MaintenanceCoverageRepository coverages, ServiceProviderRepository providers,
            ApprovalRequestRepository requests, ApprovalActionRepository actions,
            UserAccountRepository users, CurrentUser currentUser, WorkflowHistory history,
            EntityManager entityManager) {
        this.items = items;
        this.coverages = coverages;
        this.providers = providers;
        this.requests = requests;
        this.actions = actions;
        this.users = users;
        this.currentUser = currentUser;
        this.history = history;
        this.entityManager = entityManager;
    }

    @Transactional
    public ItemWorkflowResponse route(Long itemId, RouteItemRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenancePlanItem item = findItem(itemId);
        checkVersion(item, command.version());
        requireApprovedPlan(item);
        requireStatus(item, PlanItemStatus.PLANNED);
        if (command.coverageId() == null)
            conflict("COVERAGE_REQUIRED", "Verified coverage must be selected explicitly");
        MaintenanceCoverage coverage = coverages.findById(command.coverageId()).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "COVERAGE_NOT_FOUND", "Coverage not found"));
        validateCoverage(item, coverage);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        item.setCoverage(coverage);
        if (coverage.getClassification() == CoverageClassification.FREE) {
            ServiceProvider provider = coverage.getProvider();
            if (provider == null) conflict("COVERAGE_PROVIDER_MISSING", "FREE coverage has no contracted provider");
            requireActive(provider);
            item.setAssignedProvider(provider);
            item.setAssignmentRoute(AssignmentRoute.UNDER_CONTRACT);
            item.setStatus(PlanItemStatus.UNDER_CONTRACT);
            history.itemTransition(item, actor, "PLANNED", "UNDER_CONTRACT", "VERIFY_FREE_COVERAGE", null, now);
        } else {
            item.setStatus(PlanItemStatus.PENDING_PROPOSAL);
            history.itemTransition(item, actor, "PLANNED", "PENDING_PROPOSAL", "CLASSIFY_NOT_FREE", null, now);
        }
        entityManager.flush();
        return response(item, null, null, null);
    }

    @Transactional
    public ItemWorkflowResponse createVendorDraft(Long itemId, CreateVendorProposalRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        MaintenancePlanItem item = findItem(itemId);
        entityManager.lock(item, LockModeType.PESSIMISTIC_WRITE);
        checkVersion(item, command.version());
        requireApprovedPlan(item);
        if (requests.existsByPlanItem_IdAndRequestTypeAndStatus(itemId,
                ApprovalRequestType.VENDOR_SELECTION, ApprovalRequestStatus.PENDING))
            conflict("PENDING_VENDOR_APPROVAL_EXISTS", "A vendor proposal is already pending");
        requireStatus(item, PlanItemStatus.PENDING_PROPOSAL);
        validateNotFree(item);
        if (requests.existsByPlanItem_IdAndRequestTypeAndStatus(itemId,
                ApprovalRequestType.VENDOR_SELECTION, ApprovalRequestStatus.DRAFT))
            conflict("VENDOR_DRAFT_EXISTS", "A vendor proposal draft already exists");
        ApprovalRequest draft = new ApprovalRequest();
        draft.setRequestType(ApprovalRequestType.VENDOR_SELECTION);
        draft.setPlanItem(item);
        draft.setStatus(ApprovalRequestStatus.DRAFT);
        draft.setCreatedByUser(actor);
        if (command.providerId() != null) draft.setProposedProvider(findActiveProvider(command.providerId()));
        draft.setRationale(trim(command.rationale()));
        draft.setWarrantyImpactNote(trim(command.warrantyImpactNote()));
        requests.save(draft);
        entityManager.flush();
        return response(item, draft, null, null);
    }

    @Transactional
    public ItemWorkflowResponse submitVendorDraft(Long requestId, SubmitVendorProposalRequest command) {
        UserAccount actor = requireRole(UserRole.PHONG_VTYT);
        ApprovalRequest draft = findVendorRequest(requestId);
        if (draft.getStatus() != ApprovalRequestStatus.DRAFT)
            conflict("VENDOR_PROPOSAL_NOT_DRAFT", "Vendor proposal is not a draft");
        MaintenancePlanItem item = draft.getPlanItem();
        checkVersion(item, command.version());
        requireApprovedPlan(item);
        requireStatus(item, PlanItemStatus.PENDING_PROPOSAL);
        validateNotFree(item);
        if (requests.existsByPlanItem_IdAndRequestTypeAndStatus(item.getId(),
                ApprovalRequestType.VENDOR_SELECTION, ApprovalRequestStatus.PENDING))
            conflict("PENDING_VENDOR_APPROVAL_EXISTS", "A vendor proposal is already pending");
        ServiceProvider provider = command.providerId() == null
                ? draft.getProposedProvider() : findActiveProvider(command.providerId());
        if (provider == null)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "PROVIDER_REQUIRED", "Proposed provider is required");
        requireActive(provider);
        String rationale = command.rationale() == null ? draft.getRationale() : trim(command.rationale());
        if (rationale == null || rationale.isBlank())
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "RATIONALE_REQUIRED", "Proposal rationale is required");
        draft.setProposedProvider(provider);
        draft.setRationale(rationale);
        if (command.warrantyImpactNote() != null)
            draft.setWarrantyImpactNote(trim(command.warrantyImpactNote()));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        draft.setStatus(ApprovalRequestStatus.PENDING);
        draft.setSubmittedAt(now);
        item.setStatus(PlanItemStatus.WAITING_VENDOR_APPROVAL);
        history.itemTransition(item, actor, "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL",
                "SUBMIT_VENDOR", null, now);
        entityManager.flush();
        return response(item, draft, null, null);
    }

    @Transactional
    public ItemWorkflowResponse decideVendor(Long requestId, DecidePlanRequest command) {
        UserAccount actor = requireRole(UserRole.BAN_GIAM_DOC);
        ApprovalRequest request = findVendorRequest(requestId);
        MaintenancePlanItem item = request.getPlanItem();
        checkVersion(item, command.version());
        if (request.getStatus() != ApprovalRequestStatus.PENDING || actions.findByRequest_Id(requestId).isPresent())
            conflict("APPROVAL_REQUEST_NOT_PENDING", "Vendor request has already been decided");
        requireApprovedPlan(item);
        requireStatus(item, PlanItemStatus.WAITING_VENDOR_APPROVAL);
        validateNotFree(item);
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
            item.setStatus(PlanItemStatus.PENDING_PROPOSAL);
            history.itemTransition(item, actor, "WAITING_VENDOR_APPROVAL", "PENDING_PROPOSAL",
                    "RECORD_VENDOR_REVISION", comment, now);
        }
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

    private void requireApprovedPlan(MaintenancePlanItem item) {
        if (item.getPlan().getStatus() != PlanStatus.APPROVED)
            conflict("PLAN_NOT_APPROVED", "Provider routing requires an approved plan");
    }

    private void requireStatus(MaintenancePlanItem item, PlanItemStatus expected) {
        if (item.getStatus() != expected)
            conflict("PLAN_ITEM_STATE_CONFLICT", "Plan item is not in the required state");
    }

    private void checkVersion(MaintenancePlanItem item, Integer expected) {
        if (expected == null || !item.getVersion().equals(expected))
            conflict("OPTIMISTIC_LOCK_CONFLICT", "Plan item has changed; reload before retrying");
    }

    private void validateNotFree(MaintenancePlanItem item) {
        if (item.getCoverage() == null) conflict("COVERAGE_REQUIRED", "Item has no selected coverage");
        validateCoverage(item, item.getCoverage());
        if (item.getCoverage().getClassification() != CoverageClassification.NOT_FREE)
            conflict("INVALID_COVERAGE_CLASSIFICATION", "External proposal needs NOT_FREE coverage");
        if (item.getAssignmentRoute() != null || item.getAssignedProvider() != null)
            conflict("PLAN_ITEM_STATE_CONFLICT", "Item already has a provider assignment");
    }

    private void validateCoverage(MaintenancePlanItem item, MaintenanceCoverage coverage) {
        if (!coverage.getEquipment().getId().equals(item.getEquipment().getId()))
            conflict("COVERAGE_EQUIPMENT_MISMATCH", "Coverage belongs to a different equipment item");
        if (coverage.getClassification() == CoverageClassification.UNKNOWN)
            conflict("COVERAGE_UNKNOWN", "Coverage classification must be verified before routing");
        if (coverage.getVerifiedByUser() == null
                || coverage.getVerifiedByUser().getRoleCode() != UserRole.PHONG_VTYT
                || coverage.getVerifiedAt() == null
                || coverage.getBasisNote() == null || coverage.getBasisNote().isBlank())
            conflict("COVERAGE_UNVERIFIED", "Coverage has no complete verification evidence");
        LocalDate onDate = item.getPlannedDate() == null
                ? item.getPlan().getPeriodStart() : item.getPlannedDate();
        if ((coverage.getEffectiveFrom() != null && coverage.getEffectiveFrom().isAfter(onDate))
                || (coverage.getEffectiveTo() != null && coverage.getEffectiveTo().isBefore(onDate)))
            conflict("COVERAGE_NOT_APPLICABLE", "Coverage does not apply on the planned maintenance date");
    }

    private ServiceProvider findActiveProvider(Long id) {
        ServiceProvider provider = providers.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "PROVIDER_NOT_FOUND", "Provider not found"));
        requireActive(provider);
        return provider;
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
                action == null ? null : action.getId(), outcome);
    }
}
