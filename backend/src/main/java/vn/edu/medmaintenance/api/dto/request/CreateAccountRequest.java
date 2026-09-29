package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.edu.medmaintenance.persistence.enums.UserRole;

public record CreateAccountRequest(@NotBlank String username, @NotBlank String password,
        @NotNull UserRole role, Long departmentId, @NotNull Boolean active) {
    @Override public String toString() {
        return "CreateAccountRequest[username=" + username + ", password=[REDACTED], role=" + role + "]";
    }
}
