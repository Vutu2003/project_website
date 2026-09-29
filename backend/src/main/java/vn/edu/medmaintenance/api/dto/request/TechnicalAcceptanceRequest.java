package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import vn.edu.medmaintenance.persistence.enums.AcceptanceResult;

public record TechnicalAcceptanceRequest(@NotNull @PositiveOrZero Integer version,
        @NotNull AcceptanceResult result, @NotBlank String conclusion,
        boolean repairRequired) { }
