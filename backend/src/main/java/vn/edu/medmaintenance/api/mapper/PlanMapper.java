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
        return new MaintenancePlanResponse(plan.getId(), plan.getTitle(), plan.getPeriodStart(),
                plan.getPeriodEnd(), plan.getStatus(), plan.getCreatedAt(),
                plan.getCreatedByUser().getId(), plan.getCreatedByUser().getDisplayName(), plan.getVersion());
    }

    public static MaintenancePlanItemResponse toItemResponse(MaintenancePlanItem item, Long planId) {
        Equipment equipment = item.getEquipment();
        Department department = item.getDepartmentAtPlan();
        ServiceProvider provider = item.getAssignedProvider();
        return new MaintenancePlanItemResponse(item.getId(), planId, equipment.getId(),
                equipment.getEquipmentCode(), equipment.getName(), department.getId(),
                department.getName(), item.getPlannedDate(), item.getStatus(),
                provider == null ? null : provider.getId(),
                provider == null ? null : provider.getName(), item.getAssignmentRoute(), item.getVersion());
    }
}
