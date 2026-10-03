package vn.edu.medmaintenance.api.dto.request;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import vn.edu.medmaintenance.persistence.enums.CoverageClassification;
import vn.edu.medmaintenance.persistence.enums.ServiceChoice;
public record PlanItemInput(@NotNull @Positive Long equipmentId,LocalDate plannedDate,
 CoverageClassification classification,@Positive Long coverageId,@Positive Long proposedProviderId,
 @Size(max=4000) String rationale,@Size(max=4000) String warrantyImpactNote,@PositiveOrZero Integer version, ServiceChoice serviceChoice) {
 public PlanItemInput(Long equipmentId,LocalDate plannedDate){this(equipmentId,plannedDate,null,null,null,null,null,null,null);}
}
