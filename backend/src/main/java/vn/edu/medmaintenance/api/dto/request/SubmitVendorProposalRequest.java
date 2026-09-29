package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SubmitVendorProposalRequest(@NotNull @PositiveOrZero Integer version,
        @Positive Long providerId, String rationale, String warrantyImpactNote) { }
