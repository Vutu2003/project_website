package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import vn.edu.medmaintenance.persistence.enums.UserRole;

public record UpdateAccountRequest(@NotNull UserRole role, Long departmentId) { }
