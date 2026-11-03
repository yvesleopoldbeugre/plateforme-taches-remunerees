package com.platform.payments.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record InitiateDepositRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Le montant doit être strictement positif.") BigDecimal amount,
        UUID paymentMethodId
) {
}
