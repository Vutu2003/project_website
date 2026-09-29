package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;

public record DecidePlanRequest(@NotNull @PositiveOrZero Integer version,
        @NotNull ApprovalOutcome outcome, String comment) { }
