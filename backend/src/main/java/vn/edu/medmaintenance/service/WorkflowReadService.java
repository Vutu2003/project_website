package vn.edu.medmaintenance.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.response.ApprovalReviewResponse;
import vn.edu.medmaintenance.api.dto.response.CoverageEvidenceResponse;
import vn.edu.medmaintenance.api.dto.response.VendorProposalDraftResponse;
import vn.edu.medmaintenance.api.exception.ResourceNotFoundException;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;
import vn.edu.medmaintenance.persistence.repository.EquipmentRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenanceCoverageRepository;
import vn.edu.medmaintenance.persistence.repository.MaintenancePlanItemRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class WorkflowReadService {
    private final CurrentUser currentUser;
    private final EquipmentRepository equipment;
    private final MaintenanceCoverageRepository coverages;
    private final MaintenancePlanItemRepository items;
    private final ApprovalRequestRepository requests;

    public WorkflowReadService(CurrentUser currentUser, EquipmentRepository equipment,
            MaintenanceCoverageRepository coverages, MaintenancePlanItemRepository items,
            ApprovalRequestRepository requests) {
        this.currentUser = currentUser;
        this.equipment = equipment;
        this.coverages = coverages;
        this.items = items;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public List<CoverageEvidenceResponse> coverageForEquipment(Long equipmentId) {
        requireRole(UserRole.PHONG_VTYT);
        PageRequests.requirePositive(equipmentId, "equipmentId");
        if (!equipment.existsById(equipmentId)) throw new ResourceNotFoundException("Equipment");
        return coverages.findDetailedEvidenceForEquipment(equipmentId).stream().map(row -> {
            ServiceProvider provider = row.getProvider();
            return new CoverageEvidenceResponse(row.getId(), equipmentId, row.getClassification(),
                    provider == null ? null : provider.getId(), provider == null ? null : provider.getName(),
                    provider == null ? null : provider.getActive(), row.getContractReference(),
                    row.getCoverageScope(), row.getEffectiveFrom(), row.getEffectiveTo(),
                    row.getVerifiedByUser() == null ? null : row.getVerifiedByUser().getDisplayName(),
                    row.getVerifiedAt(), row.getBasisNote());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalReviewResponse pendingReview(Long requestId) {
        requireRole(UserRole.BAN_GIAM_DOC);
        PageRequests.requirePositive(requestId, "requestId");
        ApprovalRequest request = requests.findReviewById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request"));
        if (request.getStatus() != ApprovalRequestStatus.PENDING)
            throw new BusinessRuleException(HttpStatus.CONFLICT, "APPROVAL_REQUEST_NOT_PENDING",
                    "Approval request is no longer pending");
        MaintenancePlanItem item = request.getPlanItem();
        MaintenancePlan plan = request.getPlan() == null && item != null ? item.getPlan() : request.getPlan();
        ServiceProvider provider = request.getProposedProvider();
        var coverage = item == null ? null : item.getCoverage();
        return new ApprovalReviewResponse(request.getId(), request.getRequestType(), request.getStatus(),
                request.getSubmittedAt(), request.getCreatedByUser().getDisplayName(),
                plan == null ? null : plan.getId(), plan == null ? null : plan.getTitle(),
                plan == null ? null : plan.getStatus(), plan == null ? null : plan.getVersion(),
                item == null ? null : item.getId(), item == null ? null : item.getStatus(),
                item == null ? null : item.getVersion(),
                item == null ? null : item.getEquipment().getEquipmentCode(),
                item == null ? null : item.getEquipment().getName(),
                coverage == null ? null : coverage.getId(),
                coverage == null ? null : coverage.getClassification(),
                coverage == null ? null : coverage.getBasisNote(),
                provider == null ? null : provider.getId(), provider == null ? null : provider.getName(),
                request.getRationale(), request.getWarrantyImpactNote());
    }

    @Transactional(readOnly = true)
    public VendorProposalDraftResponse vendorDraft(Long itemId) {
        requireRole(UserRole.PHONG_VTYT);
        PageRequests.requirePositive(itemId, "itemId");
        if (!items.existsById(itemId)) throw new ResourceNotFoundException("Plan item");
        ApprovalRequest draft = requests.findByPlanItem_IdAndRequestTypeAndStatus(itemId,
                ApprovalRequestType.VENDOR_SELECTION, ApprovalRequestStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor proposal draft"));
        ServiceProvider provider = draft.getProposedProvider();
        return new VendorProposalDraftResponse(draft.getId(), itemId, draft.getPlanItem().getVersion(),
                provider == null ? null : provider.getId(), provider == null ? null : provider.getName(),
                draft.getRationale(), draft.getWarrantyImpactNote());
    }

    private void requireRole(UserRole role) {
        if (currentUser.get().role() != role)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED",
                    "Workflow role required");
    }
}
