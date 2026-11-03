package com.platform.wallet.api.dto;

import com.platform.wallet.domain.WalletTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletTransactionResponse(
        UUID id, String type, BigDecimal amount, BigDecimal balanceAfter, String referenceType,
        UUID referenceId, Instant createdAt
) {
    public static WalletTransactionResponse of(WalletTransaction tx) {
        return new WalletTransactionResponse(tx.getId(), tx.getType().name(), tx.getAmount(), tx.getBalanceAfter(),
                tx.getReferenceType().name(), tx.getReferenceId(), tx.getCreatedAt());
    }
}
