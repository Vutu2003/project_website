package vn.edu.medmaintenance.api.dto.response;

import vn.edu.medmaintenance.persistence.enums.AcceptanceResult;
import vn.edu.medmaintenance.persistence.enums.AcceptanceType;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

public record AcceptanceWorkflowResponse(Long acceptanceId, Long executionId,
        AcceptanceType type, AcceptanceResult result, PlanItemStatus itemStatus,
        Integer itemVersion, PlanStatus planStatus, Integer planVersion) { }
