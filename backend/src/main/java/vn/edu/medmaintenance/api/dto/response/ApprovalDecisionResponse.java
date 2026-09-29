package vn.edu.medmaintenance.api.dto.response;

import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

public record ApprovalDecisionResponse(Long requestId, Long actionId, Long planId,
        ApprovalOutcome outcome, PlanStatus planStatus, Integer planVersion) implements ApprovalDecisionResult { }
