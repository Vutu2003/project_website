package vn.edu.medmaintenance.api.dto.response;

import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.AssignmentRoute;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;

public record ItemWorkflowResponse(Long itemId, PlanItemStatus status, Integer version,
        AssignmentRoute assignmentRoute, Long providerId, Long coverageId,
        Long approvalRequestId, ApprovalRequestStatus approvalStatus,
        Long approvalActionId, ApprovalOutcome outcome) implements ApprovalDecisionResult { }
