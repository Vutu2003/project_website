package vn.edu.medmaintenance.api.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        OffsetDateTime timestamp, int status, String error, String code,
        String message, String path, List<FieldErrorResponse> fieldErrors) { }
