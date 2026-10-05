package vn.edu.medmaintenance.api.dto.response;

import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;

public record ApprovalRequestResponse(
        Long id, ApprovalRequestType requestType, ApprovalRequestStatus status,
        OffsetDateTime submittedAt, Long createdByUserId, String createdByName,
        Long planId, String planTitle, Long planItemId, String equipmentCode,
        Long proposedProviderId, String proposedProviderName,Integer planYear,String planQuarter,Long equipmentCount) { }
