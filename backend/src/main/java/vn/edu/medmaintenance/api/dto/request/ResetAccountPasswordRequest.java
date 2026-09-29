package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResetAccountPasswordRequest(@NotBlank String newPassword) {
    @Override public String toString() { return "ResetAccountPasswordRequest[newPassword=[REDACTED]]"; }
}
