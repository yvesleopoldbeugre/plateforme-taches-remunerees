package com.platform.validation.api.dto;

import jakarta.validation.constraints.NotNull;
import com.platform.validation.domain.ValidationDecision;

public record DecideValidationRequest(
        @NotNull ValidationDecision decision,
        String reasonCode,
        String comment
) {
}
