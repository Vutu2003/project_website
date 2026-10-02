package vn.edu.medmaintenance.api.dto.response;

import java.util.List;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;

/** Read-only evidence from existing plan, execution and acceptance records. */
public record ReportEvidenceResponse(Long planId, List<Item> items) {
    public record Item(Long itemId, String equipmentCode, String equipmentName,
            String departmentName, PlanItemStatus status, String providerName,
            List<EquipmentHistoryResponse.Attempt> attempts) { }
}
