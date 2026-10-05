package vn.edu.medmaintenance.api.mapper;

import vn.edu.medmaintenance.api.dto.response.MaintenancePlanItemResponse;
import vn.edu.medmaintenance.api.dto.response.MaintenancePlanResponse;
import vn.edu.medmaintenance.persistence.entity.Department;
import vn.edu.medmaintenance.persistence.entity.Equipment;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;

public final class PlanMapper {
    private PlanMapper() { }

    public static MaintenancePlanResponse toResponse(MaintenancePlan plan) {
        return toResponse(plan, null);
    }

    public static MaintenancePlanResponse toResponse(MaintenancePlan plan, Boolean pendingVendorApproval) {
        return new MaintenancePlanResponse(plan.getId(), plan.getTitle(), plan.getPeriodStart(),
                plan.getPeriodEnd(), plan.getStatus(), plan.getCreatedAt(),
                plan.getCreatedByUser().getId(), plan.getCreatedByUser().getDisplayName(), plan.getVersion(), pendingVendorApproval, plan.getPlanYear(), plan.getPlanQuarter(), null);
    }

    public static MaintenancePlanItemResponse toItemResponse(MaintenancePlanItem item, Long planId) {
        return toItemResponse(item,planId,null);
    }
    public static MaintenancePlanItemResponse toItemResponse(MaintenancePlanItem item,Long planId,vn.edu.medmaintenance.persistence.entity.ApprovalRequest proposal) {
        Equipment equipment = item.getEquipment();
        Department department = item.getDepartmentAtPlan();
        ServiceProvider provider = item.getAssignedProvider();
        return new MaintenancePlanItemResponse(item.getId(), planId, equipment.getId(),
                equipment.getEquipmentCode(), equipment.getName(), department.getId(),
                department.getName(), item.getPlannedDate(), item.getStatus(),
                provider == null ? null : provider.getId(),
                provider == null ? null : provider.getName(), item.getAssignmentRoute(), item.getVersion(),
                item.getStatus()==vn.edu.medmaintenance.persistence.enums.PlanItemStatus.PLANNED?null:
                item.getAssignmentRoute()==vn.edu.medmaintenance.persistence.enums.AssignmentRoute.UNDER_CONTRACT || item.getStatus()==vn.edu.medmaintenance.persistence.enums.PlanItemStatus.UNDER_CONTRACT?vn.edu.medmaintenance.persistence.enums.CoverageClassification.FREE:vn.edu.medmaintenance.persistence.enums.CoverageClassification.NOT_FREE,
                item.getCoverage()==null?null:item.getCoverage().getId(),
                proposal==null||proposal.getProposedProvider()==null?null:proposal.getProposedProvider().getId(),
                proposal==null||proposal.getProposedProvider()==null?null:proposal.getProposedProvider().getName(),
                proposal==null?null:proposal.getRationale(),proposal==null?null:proposal.getWarrantyImpactNote(), item.getServiceChoice(),
                item.getLastMaintenanceDate(),item.getMaintenanceDueDate(),
                item.getContract()!=null?item.getContract().getId():item.getCoverage()==null || item.getCoverage().getContract()==null?null:item.getCoverage().getContract().getId(),
                item.getContract()!=null?item.getContract().getCode():item.getCoverage()==null?null:item.getCoverage().getContractReference(),
                item.getContract()!=null?item.getContract().getStartDate():item.getCoverage()==null?null:item.getCoverage().getEffectiveFrom(),item.getContract()!=null?item.getContract().getEndDate():item.getCoverage()==null?null:item.getCoverage().getEffectiveTo());
    }
}
