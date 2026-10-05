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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ApprovalController(ApprovalRequestRepository requests, ApprovalDecisionFacade approval,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.requests = requests;
        this.approval = approval;this.jdbc=jdbc;
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
        var summary=new java.util.HashMap<Long,java.util.Map<String,Object>>();
        if(!result.isEmpty())for(var row:jdbc.queryForList("SELECT r.id,p.id related_plan_id,p.title related_plan_title,p.plan_year,p.plan_quarter,extract(year from p.period_start)::integer legacy_year,'Q'||extract(quarter from p.period_start)::integer legacy_quarter,(SELECT count(*) FROM maintenance_plan_item x WHERE x.plan_id=p.id) equipment_count FROM approval_request r LEFT JOIN maintenance_plan_item i ON i.id=r.plan_item_id JOIN maintenance_plan p ON p.id=coalesce(r.plan_id,i.plan_id) WHERE r.id IN ("+String.join(",", java.util.Collections.nCopies(result.getNumberOfElements(),"?"))+")",result.getContent().stream().map(r->(Object)r.getId()).toArray()))summary.put(((Number)row.get("id")).longValue(),row);
        return PageResponse.from(result, r->{var p=ApprovalRequestMapper.toResponse(r);var s=summary.get(p.id());
            return new ApprovalRequestResponse(p.id(),p.requestType(),p.status(),p.submittedAt(),p.createdByUserId(),p.createdByName(),p.planId()!=null?p.planId():s==null?null:((Number)s.get("related_plan_id")).longValue(),p.planTitle()!=null?p.planTitle():s==null?null:(String)s.get("related_plan_title"),p.planItemId(),p.equipmentCode(),p.proposedProviderId(),p.proposedProviderName(),s==null?null:((Number)s.get(s.get("plan_year")==null?"legacy_year":"plan_year")).intValue(),s==null?null:(String)s.get(s.get("plan_quarter")==null?"legacy_quarter":"plan_quarter"),s==null?null:((Number)s.get("equipment_count")).longValue());});
    }
}
