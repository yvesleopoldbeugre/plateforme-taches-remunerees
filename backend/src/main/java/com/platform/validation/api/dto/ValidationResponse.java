package com.platform.validation.api.dto;

import com.platform.validation.domain.Validation;
import java.time.Instant;
import java.util.UUID;

public record ValidationResponse(
        UUID id, UUID submissionId, String decision, String reasonCode, UUID reviewedBy,
        String comment, Instant reviewedAt
) {
    public static ValidationResponse of(Validation v) {
        return new ValidationResponse(v.getId(), v.getSubmissionId(), v.getDecision().name(), v.getReasonCode(),
                v.getReviewedBy(), v.getComment(), v.getReviewedAt());
    }
}
