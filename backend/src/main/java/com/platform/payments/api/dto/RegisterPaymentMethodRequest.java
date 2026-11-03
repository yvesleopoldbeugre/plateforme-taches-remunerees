package com.platform.payments.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterPaymentMethodRequest(
        @NotBlank String provider,
        @NotBlank String type,
        @NotBlank String maskedIdentifier
) {
}
