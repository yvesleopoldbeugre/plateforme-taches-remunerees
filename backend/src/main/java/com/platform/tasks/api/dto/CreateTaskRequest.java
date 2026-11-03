package com.platform.tasks.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateTaskRequest(
        @NotBlank String type,
        @NotBlank String title,
        @NotBlank String instructions,
        @NotNull @Positive BigDecimal rewardAmount,
        @NotNull @Positive Integer maxParticipants,
        @NotBlank String expectedProofType,
        String autoValidationRules,
        Integer reservationTtlMinutes
) {
}
