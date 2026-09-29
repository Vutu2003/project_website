package vn.edu.medmaintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProgressRequest(@NotBlank String workNote, String damageNote) { }
