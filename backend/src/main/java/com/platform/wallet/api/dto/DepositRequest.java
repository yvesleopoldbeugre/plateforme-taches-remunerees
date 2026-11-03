package com.platform.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DepositRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Le montant doit être strictement positif.") BigDecimal amount
) {
}
