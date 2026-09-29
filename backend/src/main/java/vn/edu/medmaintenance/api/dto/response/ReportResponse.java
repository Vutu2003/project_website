package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;
import vn.edu.medmaintenance.persistence.enums.ReportStatus;

public record ReportResponse(Long id, Long planId, ReportStatus status, PlanStatus planStatus,
        Integer planVersion, LocalDate reportDate, OffsetDateTime finalizedAt,
        String reportNumber, String workDone, String achieved, String notAchieved,
        String causes, String nextWork, String resolutions, String recommendations,
        long completedCount, long repairRequiredCount) { }
