package vn.edu.medmaintenance.api.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import vn.edu.medmaintenance.service.MaintenanceQuarter;
public record QuarterlyPlanRequest(@NotNull @Min(2000) @Max(2100) Integer year,
 @NotNull MaintenanceQuarter quarter, @Valid List<Proposal> proposals) {
 public record Proposal(@NotNull Long equipmentId,Long proposedProviderId,String rationale,Integer version){}
}
