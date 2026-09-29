package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.medmaintenance.api.dto.request.DecidePlanRequest;
import vn.edu.medmaintenance.api.dto.response.ApprovalDecisionResult;
import vn.edu.medmaintenance.service.ApprovalDecisionFacade;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.ApprovalRequestResponse;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.api.mapper.ApprovalRequestMapper;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;
import vn.edu.medmaintenance.persistence.repository.ApprovalRequestRepository;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private static final Set<String> SORT_FIELDS = Set.of("submittedAt", "id");
    private final ApprovalRequestRepository requests;
    private final ApprovalDecisionFacade approval;

    public ApprovalController(ApprovalRequestRepository requests, ApprovalDecisionFacade approval) {
        this.requests = requests;
        this.approval = approval;
    }

    @PostMapping("/{requestId}/decision")
    public ApprovalDecisionResult decide(@PathVariable Long requestId,
            @Valid @RequestBody DecidePlanRequest command) {
        return approval.decide(requestId, command);
    }

    @GetMapping("/pending")
    public PageResponse<ApprovalRequestResponse> pending(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) ApprovalRequestType requestType) {
        Pageable pageable = PageRequests.create(query, SORT_FIELDS, "submittedAt", Sort.Direction.ASC);
        var result = requestType == null
                ? requests.findByStatus(ApprovalRequestStatus.PENDING, pageable)
                : requests.findByStatusAndRequestType(ApprovalRequestStatus.PENDING, requestType, pageable);
        return PageResponse.from(result, ApprovalRequestMapper::toResponse);
    }
}
