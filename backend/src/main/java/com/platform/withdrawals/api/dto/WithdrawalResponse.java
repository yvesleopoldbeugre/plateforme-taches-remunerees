package com.platform.withdrawals.api.dto;

import com.platform.withdrawals.domain.Withdrawal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalResponse(
        UUID id, UUID userId, UUID walletId, UUID paymentMethodId, BigDecimal amount, BigDecimal fees,
        BigDecimal netAmount, String status, String rejectionReason, Instant requestedAt, Instant processedAt
) {
    public static WithdrawalResponse of(Withdrawal w) {
        return new WithdrawalResponse(w.getId(), w.getUserId(), w.getWalletId(), w.getPaymentMethodId(), w.getAmount(),
                w.getFees(), w.getNetAmount(), w.getStatus().name(), w.getRejectionReason(), w.getRequestedAt(), w.getProcessedAt());
    }
}
