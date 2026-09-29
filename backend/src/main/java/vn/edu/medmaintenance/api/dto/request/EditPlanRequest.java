package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** PATCH semantics: omitted items remain; supplied items are added or have their dates updated. */
public record EditPlanRequest(@NotNull @PositiveOrZero Integer version, @NotBlank String title,
        @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
        List<@NotNull @Valid PlanItemInput> items, List<@Positive Long> removeEquipmentIds) { }
