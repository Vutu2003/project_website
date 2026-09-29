package vn.edu.medmaintenance.api.dto.response;

import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

public record ExecutionWorkflowResponse(Long executionId, Long itemId, Integer attemptNo,
        Long providerId, PlanItemStatus itemStatus, Integer itemVersion,
        PlanStatus planStatus, Integer planVersion) { }
