package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.api.dto.request.CreateVendorProposalRequest;
import vn.edu.medmaintenance.api.dto.request.RouteItemRequest;
import vn.edu.medmaintenance.api.dto.response.ItemWorkflowResponse;
import vn.edu.medmaintenance.service.MaintenanceAssignmentService;

@RestController
@RequestMapping("/api/plan-items")
public class PlanItemCommandController {
    private final MaintenanceAssignmentService assignment;

    public PlanItemCommandController(MaintenanceAssignmentService assignment) {
        this.assignment = assignment;
    }

    @PostMapping("/{itemId}/route")
    public ItemWorkflowResponse route(@PathVariable Long itemId, @Valid @RequestBody RouteItemRequest command) {
        return assignment.route(itemId, command);
    }

    @PostMapping("/{itemId}/vendor-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemWorkflowResponse createDraft(@PathVariable Long itemId,
            @Valid @RequestBody CreateVendorProposalRequest command) {
        return assignment.createVendorDraft(itemId, command);
    }
}
