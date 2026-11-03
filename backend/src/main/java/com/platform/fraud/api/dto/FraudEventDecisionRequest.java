package com.platform.fraud.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Reused for review/dismiss/confirm — every decision on a fraud event is justified with a note. */
public record FraudEventDecisionRequest(@NotBlank String note) {
}
