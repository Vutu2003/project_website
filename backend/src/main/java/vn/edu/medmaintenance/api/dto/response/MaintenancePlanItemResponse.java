package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import vn.edu.medmaintenance.persistence.enums.AssignmentRoute;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;

public record MaintenancePlanItemResponse(
        Long id, Long planId, Long equipmentId, String equipmentCode, String equipmentName,
        Long departmentIdAtPlan, String departmentNameAtPlan, LocalDate plannedDate,
        PlanItemStatus status, Long assignedProviderId, String assignedProviderName,
        AssignmentRoute assignmentRoute, Integer version) { }
