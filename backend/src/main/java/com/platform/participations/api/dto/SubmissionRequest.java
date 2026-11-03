package com.platform.participations.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record SubmissionRequest(
        @NotEmpty Map<String, Object> content,
        List<Map<String, Object>> attachments
) {
}
