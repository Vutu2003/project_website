package vn.edu.medmaintenance.service;

import java.time.*;
import java.util.*;
import java.util.Objects;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import vn.edu.medmaintenance.api.dto.request.PlanItemInput;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.*;
import vn.edu.medmaintenance.persistence.repository.*;

@Service public class PlanningDecisionService {
    private final MaintenanceCoverageRepository coverages;
    private final ServiceProviderRepository providers;
    private final ApprovalRequestRepository requests;
    private final MaintenancePlanItemRepository items;
    private final WorkflowHistory history;
    private final NotificationService notifications;
    private final EntityManager em;
    public PlanningDecisionService(MaintenanceCoverageRepository c, ServiceProviderRepository p, ApprovalRequestRepository r, MaintenancePlanItemRepository i, WorkflowHistory h, NotificationService n, EntityManager em) {
        coverages=c;
        providers=p;
        requests=r;
        items=i;
        history=h;
        notifications=n;
        this.em=em;
    }
    public static boolean validFree(MaintenanceCoverage c, Long equipment, LocalDate date) {
        return c!=null && c.getClassification()==CoverageClassification.FREE && c.getEquipment().getId().equals(equipment) && c.getVerifiedByUser()!=null && c.getVerifiedByUser().getRoleCode()==UserRole.PHONG_VTYT && c.getVerifiedAt()!=null && c.getBasisNote()!=null && !c.getBasisNote().isBlank() && c.getProvider()!=null && Boolean.TRUE.equals(c.getProvider().getActive()) && (c.getEffectiveFrom()==null || !c.getEffectiveFrom().isAfter(date)) && (c.getEffectiveTo()==null || !c.getEffectiveTo().isBefore(date));
    }
    private LocalDate date(MaintenancePlanItem i) {
        return i.getPlannedDate()==null?i.getPlan().getPeriodStart():i.getPlannedDate();
    }
    public void apply(MaintenancePlanItem item, PlanItemInput input, UserAccount actor) {
        if (input.classification()==null)return;
        // Incomplete drafts can be saved, never submitted.
        if (item.getPlan().getStatus()!=PlanStatus.DRAFT && item.getPlan().getStatus()!=PlanStatus.REVISION_REQUIRED)fail("PLAN_NOT_EDITABLE", "Kế hoạch đã khóa hình thức bảo trì.");
        if (input.version()!=null && !input.version().equals(item.getVersion()))fail("OPTIMISTIC_LOCK_CONFLICT", "Hạng mục đã thay đổi, hãy tải lại.");
        String old=item.getStatus().name();
        var active=requests.findByPlanItem_IdOrderBySubmittedAtAscIdAsc(item.getId()).stream().filter(q->q.getRequestType()==ApprovalRequestType.VENDOR_SELECTION && (q.getStatus()==ApprovalRequestStatus.DRAFT || q.getStatus()==ApprovalRequestStatus.PENDING)).toList();
        var at=OffsetDateTime.now(ZoneOffset.UTC);
        MaintenanceCoverage coverage=null;
        ServiceProvider proposed=null;
        if (input.classification()==CoverageClassification.FREE) {
            coverage=input.coverageId()==null?null:coverages.findById(input.coverageId()).orElse(null);
            if (!validFree(coverage, item.getEquipment().getId(), date(item)))fail("INVALID_FREE_COVERAGE", "Thiết bị "+item.getEquipment().getEquipmentCode()+" cần hợp đồng hợp lệ, đã xác minh và đơn vị hoạt động.");
        }
        else if (input.proposedProviderId()!=null) {
            proposed=providers.findById(input.proposedProviderId()).orElse(null);
            if (proposed==null || !Boolean.TRUE.equals(proposed.getActive()))fail("PROVIDER_INACTIVE", "Đơn vị đề xuất không tồn tại hoặc ngừng hoạt động.");
        }
        if (input.classification()==CoverageClassification.NOT_FREE && active.size()==1) {
            var q=active.get(0);
            if (q.getStatus()==ApprovalRequestStatus.DRAFT && Objects.equals(q.getProposedProvider()==null?null:q.getProposedProvider().getId(), input.proposedProviderId()) && Objects.equals(q.getRationale(), trim(input.rationale())) && Objects.equals(q.getWarrantyImpactNote(), trim(input.warrantyImpactNote())) && item.getStatus()==PlanItemStatus.PENDING_PROPOSAL) return;
        }
        for (var q:active) {
            q.setStatus(ApprovalRequestStatus.CANCELLED);
            q.setResolvedAt(at);
        }
        em.flush();
        // release the active-content unique key before inserting replacement
        item.setCoverage(coverage);
        item.setAssignedProvider(null);
        item.setAssignmentRoute(null);
        if (input.classification()==CoverageClassification.FREE) {
            item.setAssignedProvider(coverage.getProvider());
            item.setAssignmentRoute(AssignmentRoute.UNDER_CONTRACT);
            item.setStatus(PlanItemStatus.UNDER_CONTRACT);
            history.itemTransition(item, actor, old, item.getStatus().name(), "SELECT_CONTRACT_COVERAGE", "coverage="+coverage.getId()+"; date="+date(item)+"; basis="+coverage.getBasisNote(), at);
        }
        else {
            item.setStatus(PlanItemStatus.PENDING_PROPOSAL);
            var q=new ApprovalRequest();
            q.setPlanItem(item);
            q.setRequestType(ApprovalRequestType.VENDOR_SELECTION);
            q.setStatus(ApprovalRequestStatus.DRAFT);
            q.setCreatedByUser(actor);
            q.setProposedProvider(proposed);
            q.setRationale(trim(input.rationale()));
            q.setWarrantyImpactNote(trim(input.warrantyImpactNote()));
            requests.save(q);
            history.itemTransition(item, actor, old, item.getStatus().name(), "PREPARE_EXTERNAL_PROVIDER", "classification=NOT_FREE; provider="+input.proposedProviderId()+"; date="+date(item)+"; basis="+trim(input.rationale())+"; note="+trim(input.warrantyImpactNote()), at);
        }
    }
    public void validateComplete(MaintenancePlanItem i) {
        em.lock(i, LockModeType.OPTIMISTIC);
        String code=i.getEquipment().getEquipmentCode();
        if (i.getStatus()==PlanItemStatus.UNDER_CONTRACT) {
            if (!validFree(i.getCoverage(), i.getEquipment().getId(), date(i)) || i.getAssignedProvider()==null || !i.getAssignedProvider().getId().equals(i.getCoverage().getProvider().getId()) || i.getAssignmentRoute()!=AssignmentRoute.UNDER_CONTRACT)fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" chưa có hợp đồng và đơn vị hợp lệ.");
        }
        else if (i.getStatus()==PlanItemStatus.PENDING_PROPOSAL) {
            var q=draft(i);
            if (q==null || q.getProposedProvider()==null)fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" chưa chọn đơn vị bảo trì đề xuất.");
            if (!Boolean.TRUE.equals(q.getProposedProvider().getActive()))fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" có đơn vị đề xuất ngừng hoạt động.");
            if (q.getRationale()==null || q.getRationale().isBlank())fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" chưa nhập căn cứ chọn đơn vị.");
        }
        else fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" chưa hoàn tất hình thức bảo trì.");
    }
    public ApprovalRequest draft(MaintenancePlanItem i) {
        return requests.findByPlanItem_IdAndRequestTypeAndStatus(i.getId(), ApprovalRequestType.VENDOR_SELECTION, ApprovalRequestStatus.DRAFT).orElse(null);
    }
    public void activate(MaintenancePlan plan, UserAccount actor, OffsetDateTime at) {
        for (var i:items.findAllByPlan_Id(plan.getId())) {
            em.lock(i, LockModeType.OPTIMISTIC);
            var q=draft(i);
            // Legacy approved plans without prepared content remain readable; no fabricated proposal.
            if (i.getStatus()!=PlanItemStatus.PENDING_PROPOSAL || q==null)continue;
            validateComplete(i);
            q.setStatus(ApprovalRequestStatus.PENDING);
            q.setSubmittedAt(at);
            i.setStatus(PlanItemStatus.WAITING_VENDOR_APPROVAL);
            history.itemTransition(i, actor, "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL", "ACTIVATE_PREPARED_VENDOR", "request="+q.getId(), at);
            // BGD actor must also receive the newly required provider review.
            notifications.notifyRole(UserRole.BAN_GIAM_DOC, null, null, "VENDOR_PENDING", "Đề xuất đơn vị chờ phê duyệt", "Thiết bị "+i.getEquipment().getEquipmentCode()+" — "+q.getProposedProvider().getName(), "/approvals/"+q.getId());
        }
    }
    public void returnForVendorRevision(MaintenancePlan plan, UserAccount actor, String reason, OffsetDateTime at) {
        plan.setStatus(PlanStatus.REVISION_REQUIRED);
        history.plan(plan, actor, "APPROVED", "REVISION_REQUIRED", "RETURN_VENDOR_REVISION", reason, at);
        for (var i:items.findAllByPlan_Id(plan.getId())) {
            if (i.getStatus()!=PlanItemStatus.WAITING_VENDOR_APPROVAL && i.getStatus()!=PlanItemStatus.ASSIGNED_EXTERNAL && i.getStatus()!=PlanItemStatus.PENDING_PROPOSAL)continue;
            var rounds=requests.findByPlanItem_IdOrderBySubmittedAtAscIdAsc(i.getId());
            var latest=rounds.stream().filter(q->q.getRequestType()==ApprovalRequestType.VENDOR_SELECTION && q.getStatus()!=ApprovalRequestStatus.CANCELLED).reduce((a, b)->b).orElse(null);
            for (var q:rounds)if (q.getStatus()==ApprovalRequestStatus.PENDING || q.getStatus()==ApprovalRequestStatus.DRAFT) {
                q.setStatus(ApprovalRequestStatus.CANCELLED);
                q.setResolvedAt(at);
            }
            em.flush();
            if (latest!=null) {
                var q=new ApprovalRequest();
                q.setPlanItem(i);
                q.setRequestType(ApprovalRequestType.VENDOR_SELECTION);
                q.setStatus(ApprovalRequestStatus.DRAFT);
                q.setCreatedByUser(actor);
                q.setProposedProvider(latest.getProposedProvider());
                q.setRationale(latest.getRationale());
                q.setWarrantyImpactNote(latest.getWarrantyImpactNote());
                requests.save(q);
            }
            var old=i.getStatus().name();
            i.setStatus(PlanItemStatus.PENDING_PROPOSAL);
            i.setAssignedProvider(null);
            i.setAssignmentRoute(null);
            history.itemTransition(i, actor, old, "PENDING_PROPOSAL", "RETURN_PROPOSAL_EDIT", reason, at);
        }
    }
    private String trim(String s) {
        return s==null?null:s.trim();
    }
    private void fail(String code, String message) {
        throw new BusinessRuleException(HttpStatus.CONFLICT, code, message);
    }
}
