package vn.edu.medmaintenance.api.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.api.dto.response.ApprovalReviewResponse;
import vn.edu.medmaintenance.api.dto.response.CoverageEvidenceResponse;
import vn.edu.medmaintenance.api.dto.response.VendorProposalDraftResponse;
import vn.edu.medmaintenance.service.WorkflowReadService;

@RestController
@RequestMapping("/api")
public class WorkflowReadController {
    private final WorkflowReadService reads;

    public WorkflowReadController(WorkflowReadService reads) { this.reads = reads; }

    @GetMapping("/equipment/{equipmentId}/coverages")
    public List<CoverageEvidenceResponse> coverages(@PathVariable Long equipmentId) {
        return reads.coverageForEquipment(equipmentId);
    }

    @GetMapping("/approvals/{requestId}")
    public ApprovalReviewResponse review(@PathVariable Long requestId) {
        return reads.pendingReview(requestId);
    }

    @GetMapping("/plan-items/{itemId}/vendor-proposals/draft")
    public VendorProposalDraftResponse draft(@PathVariable Long itemId) {
        return reads.vendorDraft(itemId);
    }
}
