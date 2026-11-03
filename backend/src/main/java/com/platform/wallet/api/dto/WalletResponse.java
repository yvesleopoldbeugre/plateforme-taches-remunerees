package com.platform.wallet.api.dto;

import com.platform.wallet.domain.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id, String ownerType, UUID ownerId, BigDecimal availableBalance, BigDecimal pendingBalance,
        BigDecimal reservedBalance, BigDecimal lifetimeEarned, BigDecimal lifetimeWithdrawn, String currency,
        Instant createdAt
) {
    public static WalletResponse of(Wallet w) {
        return new WalletResponse(w.getId(), w.getOwnerType().name(), w.getOwnerId(), w.getAvailableBalance(),
                w.getPendingBalance(), w.getReservedBalance(), w.getLifetimeEarned(), w.getLifetimeWithdrawn(),
                w.getCurrency(), w.getCreatedAt());
    }
}
