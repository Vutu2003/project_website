package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.CoverageClassification;
import vn.edu.medmaintenance.persistence.enums.UserRole;

/** Existing coverage evidence for explicit UC05 selection; routing remains a command decision. */
public record CoverageEvidenceResponse(Long id, Long equipmentId, CoverageClassification classification,
        Long providerId, String providerName, Boolean providerActive, String contractReference,
        String coverageScope, LocalDate effectiveFrom, LocalDate effectiveTo,
        String verifiedByName, OffsetDateTime verifiedAt, String basisNote, UserRole verifiedByRole, LocalDate warrantyExpiresOn, String warrantyStatus) { }
