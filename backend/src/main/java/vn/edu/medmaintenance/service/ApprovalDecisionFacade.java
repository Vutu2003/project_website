package vn.edu.medmaintenance.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.medmaintenance.api.dto.request.DecidePlanRequest;
import vn.edu.medmaintenance.api.dto.response.ApprovalDecisionResult;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;

@Service
public class ApprovalDecisionFacade {
    private final ApprovalRequestRepository requests;
    private final PlanApprovalService plans;
    private final MaintenanceAssignmentService vendors;

    public ApprovalDecisionFacade(ApprovalRequestRepository requests, PlanApprovalService plans,
            MaintenanceAssignmentService vendors) {
        this.requests = requests;
        this.plans = plans;
        this.vendors = vendors;
    }

    public ApprovalDecisionResult decide(Long requestId, DecidePlanRequest command) {
        if (requestId == null || requestId <= 0)
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Request ID must be positive");
        var request = requests.findById(requestId).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND", "Approval request not found"));
        // Each transactional service reloads and validates the typed target before writing.
        return request.getRequestType() == ApprovalRequestType.PLAN_APPROVAL
                ? plans.decide(requestId, command) : vendors.decideVendor(requestId, command);
    }
}
