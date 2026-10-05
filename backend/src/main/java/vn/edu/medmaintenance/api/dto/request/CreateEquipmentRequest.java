package vn.edu.medmaintenance.api.dto.request;
import java.time.LocalDate;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.edu.medmaintenance.service.MaintenanceQuarter;
public record CreateEquipmentRequest(@NotBlank @Size(max=100) String equipmentCode,
 @NotBlank @Size(max=300) String name,@NotNull @Positive Long departmentId,
 @Size(max=200) String model,@Size(max=200) String serialNumber,
 @NotNull @Size(min=1,max=4) Set<@NotNull MaintenanceQuarter> quarters,
 LocalDate warrantyEndDate,@Positive Long contractId,@Valid NewContract newContract) {
 public record NewContract(@NotBlank @Size(max=100) String code,@NotBlank @Size(max=300) String name,
  @NotNull LocalDate startDate,@NotNull LocalDate endDate,@Positive Long providerId,@Valid NewCompany newCompany) {}
 public record NewCompany(@NotBlank @Size(max=100) String code,@NotBlank @Size(max=300) String name,@Size(max=1000) String contact) {}
}
