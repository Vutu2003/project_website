package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.api.dto.request.SubmitVendorProposalRequest;
import vn.edu.medmaintenance.api.dto.response.ItemWorkflowResponse;
import vn.edu.medmaintenance.service.MaintenanceAssignmentService;

@RestController
@RequestMapping("/api/vendor-proposals")
public class VendorProposalController {
    private final MaintenanceAssignmentService assignment;

    public VendorProposalController(MaintenanceAssignmentService assignment) {
        this.assignment = assignment;
    }

    @PostMapping("/{requestId}/submit")
    public ItemWorkflowResponse submit(@PathVariable Long requestId,
            @Valid @RequestBody SubmitVendorProposalRequest command) {
        return assignment.submitVendorDraft(requestId, command);
    }
}
