package vn.edu.medmaintenance.api.dto.request;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateWarrantyRequest(@Positive Long manufacturerProviderId,
        @NotNull @Size(max=100) List<@Valid Contract> contracts) {
    public record Contract(@NotNull @Positive Long id, LocalDate warrantyExpiresOn) { }
}
