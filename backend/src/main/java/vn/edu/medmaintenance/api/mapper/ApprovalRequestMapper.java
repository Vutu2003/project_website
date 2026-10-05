package vn.edu.medmaintenance.api.mapper;

import vn.edu.medmaintenance.api.dto.response.ApprovalRequestResponse;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;

public final class ApprovalRequestMapper {
    private ApprovalRequestMapper() { }

    public static ApprovalRequestResponse toResponse(ApprovalRequest request) {
        MaintenancePlan plan = request.getPlan();
        MaintenancePlanItem item = request.getPlanItem();
        ServiceProvider provider = request.getProposedProvider();
        return new ApprovalRequestResponse(request.getId(), request.getRequestType(),
                request.getStatus(), request.getSubmittedAt(), request.getCreatedByUser().getId(),
                request.getCreatedByUser().getDisplayName(),
                plan == null ? null : plan.getId(), plan == null ? null : plan.getTitle(),
                item == null ? null : item.getId(),
                item == null ? null : item.getEquipment().getEquipmentCode(),
                provider == null ? null : provider.getId(),
                provider == null ? null : provider.getName(),plan==null?null:plan.getPlanYear(),plan==null?null:plan.getPlanQuarter(),null);
    }
}
