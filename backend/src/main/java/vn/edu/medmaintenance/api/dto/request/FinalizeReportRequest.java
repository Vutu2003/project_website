package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FinalizeReportRequest(@NotNull @PositiveOrZero Integer version) { }
