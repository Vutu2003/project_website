package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProgressRequest(MaintenanceProgressStatus status, @Size(max = 4000) String note,
        @Min(0) Integer version, String workNote, String damageNote) { }
