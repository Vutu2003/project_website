package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RepairHandoffRequest(@NotNull @PositiveOrZero Integer version,
        @NotBlank String reason) { }
