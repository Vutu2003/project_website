package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StartExecutionRequest(@NotNull @PositiveOrZero Integer version,
        @NotNull @PositiveOrZero Integer planVersion) { }
