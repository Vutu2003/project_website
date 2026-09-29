package vn.edu.medmaintenance.api.dto.response;

import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.enums.CoverageClassification;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

/** Read-only subject context for a pending director decision. */
public record ApprovalReviewResponse(Long id, ApprovalRequestType requestType,
        ApprovalRequestStatus status, OffsetDateTime submittedAt, String createdByName,
        Long planId, String planTitle, PlanStatus planStatus, Integer planVersion,
        Long planItemId, PlanItemStatus itemStatus, Integer itemVersion,
        String equipmentCode, String equipmentName, Long coverageId,
        CoverageClassification coverageClassification, String coverageBasis,
        Long proposedProviderId, String proposedProviderName,
        String rationale, String warrantyImpactNote) { }
