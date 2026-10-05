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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public PlanningDecisionService(MaintenanceCoverageRepository c, ServiceProviderRepository p, ApprovalRequestRepository r, MaintenancePlanItemRepository i, WorkflowHistory h, NotificationService n, EntityManager em, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        coverages=c;
        providers=p;
        requests=r;
        items=i;
        history=h;
        notifications=n;
        this.em=em;this.jdbc=jdbc;
    }
    public static boolean validFree(MaintenanceCoverage c, Long equipment, LocalDate date) {
        if(c==null || !c.getEquipment().getId().equals(equipment))return false;
        var k=c.getContract();
        if(k!=null)return Boolean.TRUE.equals(k.getActive()) && Boolean.TRUE.equals(k.getProvider().getActive())
            && !date.isBefore(k.getStartDate()) && !date.isAfter(k.getEndDate());
        // Legacy coverage inserted by old integrations remains compatible until normalized.
        return c.getClassification()==CoverageClassification.FREE && c.getVerifiedByUser()!=null
            && c.getVerifiedByUser().getRoleCode()==UserRole.PHONG_VTYT && c.getVerifiedAt()!=null
            && c.getBasisNote()!=null && !c.getBasisNote().isBlank() && c.getProvider()!=null
            && Boolean.TRUE.equals(c.getProvider().getActive())
            && (c.getEffectiveFrom()==null || !date.isBefore(c.getEffectiveFrom()))
            && (c.getEffectiveTo()==null || !date.isAfter(c.getEffectiveTo()));
    }
    public static List<MaintenanceCoverage> eligibleCoverages(List<MaintenanceCoverage> evidence,Long equipmentId,LocalDate date){
        var contracts=new java.util.TreeMap<Long,MaintenanceCoverage>();
        for(var c:evidence)if(validFree(c,equipmentId,date)){
            // Negative keys distinguish legacy evidence from normalized contract identities.
            long key=c.getContract()==null?-c.getId():c.getContract().getId();
            contracts.merge(key,c,(a,b)->a.getId()>b.getId()?a:b);
        }
        return new ArrayList<>(contracts.values());
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Map<String,Object> preview(Long equipmentId,LocalDate referenceDate){
        var eligible=eligibleCoverages(coverages.findDetailedEvidenceForEquipment(equipmentId),equipmentId,referenceDate);
        var result=new HashMap<String,Object>();
        result.put("classification",eligible.isEmpty()?"NOT_FREE":"FREE");result.put("conflict",eligible.size()>1);
        if(eligible.size()==1){var c=eligible.get(0);result.put("coverageId",c.getId());result.put("providerId",c.getProvider().getId());result.put("providerName",c.getProvider().getName());
            result.put("contractId",c.getContract()==null?null:c.getContract().getId());result.put("contractCode",c.getContractReference());
            result.put("contractStartDate",c.getEffectiveFrom());result.put("contractEndDate",c.getEffectiveTo());}
        return result;
    }
    private LocalDate date(MaintenancePlanItem i) {
        return i.getPlannedDate()==null?i.getPlan().getPeriodStart():i.getPlannedDate();
    }
    private List<MaintenanceCoverage> eligible(MaintenancePlanItem item,boolean capture,UserAccount actor){
        if(item.getPlan().getPlanYear()==null)return eligibleCoverages(coverages.findEvidenceForEquipment(item.getEquipment().getId()),item.getEquipment().getId(),date(item));
        // Contract membership is the source of truth. Coverage is only retained execution evidence.
        var ids=jdbc.queryForList("SELECT k.id FROM maintenance_contract_equipment m JOIN maintenance_contract k ON k.id=m.contract_id JOIN service_provider p ON p.id=k.provider_id WHERE m.equipment_id=? AND k.active AND p.active AND ?::date BETWEEN k.start_date AND k.end_date ORDER BY k.id",Long.class,item.getEquipment().getId(),date(item));
        if(ids.size()>1)fail("CONTRACT_CONFLICT","Thiết bị "+item.getEquipment().getEquipmentCode()+" có nhiều hợp đồng hợp lệ.");
        if(ids.isEmpty())return List.of();
        long contractId=ids.get(0);
        var evidence=coverages.findDetailedEvidenceForEquipment(item.getEquipment().getId()).stream().filter(c->c.getContract()!=null && c.getContract().getId()==contractId).max(Comparator.comparing(MaintenanceCoverage::getId));
        if(evidence.isEmpty() && capture){
            long evidenceId=jdbc.queryForObject("INSERT INTO maintenance_coverage(equipment_id,contract_id,provider_id,classification,verified_by_user_id,verified_at,basis_note) SELECT ?,id,provider_id,'FREE',?,now(),'Danh mục thiết bị thuộc hợp đồng' FROM maintenance_contract WHERE id=? RETURNING id",Long.class,item.getEquipment().getId(),actor.getId(),contractId);
            return List.of(coverages.findById(evidenceId).orElseThrow());
        }
        return evidence.map(List::of).orElseGet(List::of);
    }
    public void apply(MaintenancePlanItem item, PlanItemInput input, UserAccount actor) {
        var eligible=eligible(item,true,actor);
        if(eligible.size()>1)fail("CONTRACT_CONFLICT","Thiết bị "+item.getEquipment().getEquipmentCode()+" có nhiều hợp đồng hợp lệ vào ngày dự kiến. Cần kiểm tra hợp đồng.");
        var derived=eligible.isEmpty()?CoverageClassification.NOT_FREE:CoverageClassification.FREE;
        input=new PlanItemInput(input.equipmentId(),input.plannedDate(),derived,
            eligible.isEmpty()?null:eligible.get(0).getId(),input.proposedProviderId(),input.rationale(),input.warrantyImpactNote(),input.version(),
            derived==CoverageClassification.FREE?null:input.serviceChoice());
        // Incomplete drafts can be saved, never submitted.
        if (item.getPlan().getStatus()!=PlanStatus.DRAFT && item.getPlan().getStatus()!=PlanStatus.REVISION_REQUIRED)fail("PLAN_NOT_EDITABLE", "Kế hoạch đã khóa hình thức bảo trì.");
        if (input.version()!=null && !input.version().equals(item.getVersion()))fail("OPTIMISTIC_LOCK_CONFLICT", "Hạng mục đã thay đổi, hãy tải lại.");
        ServiceChoice choice=input.classification()==CoverageClassification.FREE?null:
            input.serviceChoice()==null?ServiceChoice.EXTERNAL:input.serviceChoice();
        if (input.classification()==CoverageClassification.FREE && input.serviceChoice()!=null)
            fail("INVALID_SERVICE_CHOICE", "Theo hợp đồng không dùng lựa chọn ngoài bảo hành.");
        if (choice==ServiceChoice.MANUFACTURER) {
            var manufacturer=item.getEquipment().getManufacturerProvider();
            if (manufacturer==null || !Boolean.TRUE.equals(manufacturer.getActive()) || !manufacturer.getId().equals(input.proposedProviderId()))
                fail("INVALID_MANUFACTURER", "Chọn nhà sản xuất đang hoạt động đã lưu trong hồ sơ thiết bị.");
        }
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
            if (q.getStatus()==ApprovalRequestStatus.DRAFT && Objects.equals(q.getProposedProvider()==null?null:q.getProposedProvider().getId(), input.proposedProviderId()) && Objects.equals(q.getRationale(), trim(input.rationale())) && Objects.equals(q.getWarrantyImpactNote(), trim(input.warrantyImpactNote())) && item.getStatus()==PlanItemStatus.PENDING_PROPOSAL && item.getServiceChoice()==choice) return;
        }
        for (var q:active) {
            q.setStatus(ApprovalRequestStatus.CANCELLED);
            q.setResolvedAt(at);
        }
        em.flush();
        // release the active-content unique key before inserting replacement
        item.setServiceChoice(choice);
        item.setCoverage(coverage);
        item.setContract(coverage==null?null:coverage.getContract());
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
            history.itemTransition(item, actor, old, item.getStatus().name(), "PREPARE_EXTERNAL_PROVIDER", "classification=NOT_FREE; serviceChoice="+choice+"; provider="+input.proposedProviderId()+"; date="+date(item)+"; basis="+trim(input.rationale())+"; note="+trim(input.warrantyImpactNote()), at);
        }
    }
    public void validateComplete(MaintenancePlanItem i) {
        em.lock(i, LockModeType.OPTIMISTIC);
        String code=i.getEquipment().getEquipmentCode();
        var valid=eligible(i,false,null);
        if(valid.size()>1)fail("CONTRACT_CONFLICT","Nhiều hợp đồng hợp lệ cho thiết bị "+code);
        if(i.getStatus()==PlanItemStatus.PENDING_PROPOSAL && !valid.isEmpty())fail("PLAN_ITEM_INCOMPLETE","Hợp đồng đã thay đổi cho thiết bị "+code+"; hãy lưu lại kế hoạch.");
        if (i.getStatus()==PlanItemStatus.UNDER_CONTRACT) {
            if (valid.isEmpty() || (i.getPlan().getPlanYear()!=null && (i.getContract()==null || !i.getContract().getId().equals(valid.get(0).getContract().getId()))) || !validFree(i.getCoverage(), i.getEquipment().getId(), date(i)) || i.getAssignedProvider()==null || !i.getAssignedProvider().getId().equals(i.getCoverage().getProvider().getId()) || i.getAssignmentRoute()!=AssignmentRoute.UNDER_CONTRACT)fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" chưa có hợp đồng và đơn vị hợp lệ.");
        }
        else if (i.getStatus()==PlanItemStatus.PENDING_PROPOSAL) {
            var q=draft(i);
            if (q==null || q.getProposedProvider()==null)fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" chưa chọn đơn vị bảo trì đề xuất.");
            if (i.getServiceChoice()==ServiceChoice.MANUFACTURER) {
                var manufacturer=i.getEquipment().getManufacturerProvider();
                if (manufacturer==null || !manufacturer.getId().equals(q.getProposedProvider().getId()))
                    fail("PLAN_ITEM_INCOMPLETE", "Thiết bị "+code+" cần chọn lại nhà sản xuất trong hồ sơ.");
            }
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
