package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import vn.edu.medmaintenance.persistence.enums.CoverageClassification;

public record MaintenanceSuggestionResponse(Long equipmentId, String equipmentCode, String equipmentName, Long departmentId, String departmentName, LocalDate lastMaintenanceDate, String latestResult, String latestStatus, CoverageClassification classification, Long coverageId, String contractReference, String contractualProviderName, String lastExternalProviderName, LocalDate suggestedDate, LocalDate referenceDate, String suggestionBasis, String coverageNote, List<Long> openPlanIds, LocalDate warrantyExpiresOn, String warrantyStatus, Long manufacturerProviderId, String manufacturerName, String manufacturerContact, String model, String serialNumber, String technicalSpec, Boolean active) {
}
