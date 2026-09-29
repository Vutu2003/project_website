package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

public record MaintenancePlanResponse(
        Long id, String title, LocalDate periodStart, LocalDate periodEnd, PlanStatus status,
        OffsetDateTime createdAt, Long createdByUserId, String createdByName, Integer version) { }
