package com.platform.withdrawals.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record RequestWithdrawalRequest(
        @NotNull UUID paymentMethodId,
        @NotNull @DecimalMin(value = "0.01", message = "Le montant doit être strictement positif.") BigDecimal amount
) {
}
