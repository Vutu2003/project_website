package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.*;
import vn.edu.medmaintenance.service.ExecutionAcceptanceService;

@RestController
@RequestMapping("/api")
public class ExecutionAcceptanceController {
    private final ExecutionAcceptanceService workflow;

    public ExecutionAcceptanceController(ExecutionAcceptanceService workflow) { this.workflow = workflow; }

    @PostMapping("/plan-items/{itemId}/executions")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionWorkflowResponse start(@PathVariable Long itemId,
            @Valid @RequestBody StartExecutionRequest request) {
        return workflow.start(itemId, request);
    }

    @PostMapping("/executions/{executionId}/progress")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressResponse progress(@PathVariable Long executionId,
            @Valid @RequestBody ProgressRequest request) {
        return workflow.addProgress(executionId, request);
    }

    @PostMapping("/executions/{executionId}/complete-work")
    public ExecutionWorkflowResponse finish(@PathVariable Long executionId,
            @Valid @RequestBody FinishExecutionRequest request) {
        return workflow.finish(executionId, request);
    }

    @PostMapping("/executions/{executionId}/repair-required")
    public ExecutionWorkflowResponse repair(@PathVariable Long executionId,
            @Valid @RequestBody RepairHandoffRequest request) {
        return workflow.repair(executionId, request);
    }

    @PostMapping("/executions/{executionId}/technical-acceptance")
    @ResponseStatus(HttpStatus.CREATED)
    public AcceptanceWorkflowResponse technical(@PathVariable Long executionId,
            @Valid @RequestBody TechnicalAcceptanceRequest request) {
        return workflow.technical(executionId, request);
    }

    @PostMapping("/executions/{executionId}/handover")
    @ResponseStatus(HttpStatus.CREATED)
    public AcceptanceWorkflowResponse handover(@PathVariable Long executionId,
            @RequestHeader(value = "X-VTYT-Authorization", required = false) String vtytAuthorization,
            @Valid @RequestBody HandoverRequest request) {
        return workflow.handover(executionId, vtytAuthorization, request);
    }
}
