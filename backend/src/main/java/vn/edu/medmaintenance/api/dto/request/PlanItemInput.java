package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record PlanItemInput(@NotNull @Positive Long equipmentId, LocalDate plannedDate) { }
