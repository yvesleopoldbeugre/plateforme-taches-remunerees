package com.platform.withdrawals.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectWithdrawalRequest(
        @NotBlank String reason
) {
}
