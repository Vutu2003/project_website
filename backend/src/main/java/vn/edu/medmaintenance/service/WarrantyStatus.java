package vn.edu.medmaintenance.service;

import java.time.LocalDate;
import vn.edu.medmaintenance.persistence.entity.MaintenanceCoverage;

public final class WarrantyStatus {
    private WarrantyStatus() { }
    public static String at(MaintenanceCoverage coverage, LocalDate date) {
        if (coverage == null || coverage.getWarrantyExpiresOn() == null) return "UNKNOWN";
        if (coverage.getEffectiveFrom() != null && date.isBefore(coverage.getEffectiveFrom())) return "NOT_STARTED";
        return date.isAfter(coverage.getWarrantyExpiresOn()) ? "EXPIRED" : "ACTIVE";
    }
}
