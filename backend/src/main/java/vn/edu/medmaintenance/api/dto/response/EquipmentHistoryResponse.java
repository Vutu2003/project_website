package vn.edu.medmaintenance.api.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import vn.edu.medmaintenance.persistence.enums.*;

public record EquipmentHistoryResponse(Long equipmentId, String equipmentCode, String equipmentName,
        Long currentDepartmentId, List<Campaign> campaigns) {
    public record Campaign(Long planId, String planTitle, PlanStatus planStatus,
            LocalDate periodStart, LocalDate periodEnd, Long itemId, Long departmentIdAtPlan,
            PlanItemStatus itemStatus, AssignmentRoute assignmentRoute, Long assignedProviderId,
            Long coverageId, List<Attempt> attempts, List<StateEvent> itemHistory,
            List<StateEvent> planHistory, ReportRef report) { }

    public record Attempt(Long executionId, int attemptNo, Long actualProviderId,
            String actualProviderName, OffsetDateTime startedAt, OffsetDateTime endedAt,
            String resultNote, List<Progress> progress, Acceptance technicalAcceptance,
            Acceptance handoverAcceptance) { }

    public record Progress(Long id, OffsetDateTime eventAt, String workNote,
            String damageNote, Long recordedByUserId) { }

    public record Acceptance(Long id, AcceptanceType type, AcceptanceResult result,
            OffsetDateTime observedAt, String conclusion, Long recordedByUserId,
            Long departmentSignerId, OffsetDateTime departmentConfirmedAt,
            Long vtytSignerId, OffsetDateTime vtytConfirmedAt) { }

    public record StateEvent(Long id, String oldState, String newState, String action,
            String reason, OffsetDateTime at, Long actorUserId) { }

    public record ReportRef(Long id, ReportStatus status, LocalDate reportDate,
            OffsetDateTime finalizedAt) { }
}
