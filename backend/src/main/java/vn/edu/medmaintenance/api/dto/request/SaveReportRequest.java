package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveReportRequest(@NotNull @PositiveOrZero Integer version,
        String reportNumber, String workDone, String achieved, String notAchieved,
        String causes, String nextWork, String resolutions, String recommendations) { }
