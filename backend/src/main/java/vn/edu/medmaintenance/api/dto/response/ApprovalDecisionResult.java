package vn.edu.medmaintenance.api.dto.response;

/** Wire DTO family returned by the typed plan/vendor approval dispatcher. */
public sealed interface ApprovalDecisionResult permits ApprovalDecisionResponse, ItemWorkflowResponse { }
