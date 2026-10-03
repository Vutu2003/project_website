package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import java.util.List;

public record WarrantyResponse(Long equipmentId, String equipmentCode, String equipmentName,
        Long manufacturerProviderId, String manufacturerName, String manufacturerContact,
        Boolean manufacturerActive, LocalDate referenceDate, List<Contract> contracts) {
    public record Contract(Long id, String contractReference, String providerName, String providerContact,
            String coverageScope, LocalDate effectiveFrom, LocalDate effectiveTo,
            LocalDate warrantyExpiresOn, String warrantyStatus, String basisNote) { }
}
