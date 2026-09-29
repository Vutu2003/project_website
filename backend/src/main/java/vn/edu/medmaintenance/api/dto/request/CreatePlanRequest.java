package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreatePlanRequest(@NotBlank String title, @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd, @NotEmpty List<@NotNull @Valid PlanItemInput> items) { }
