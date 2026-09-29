package vn.edu.medmaintenance.api.dto.response;

import java.time.OffsetDateTime;

public record ProgressResponse(Long progressId, Long executionId, OffsetDateTime eventAt) { }
