package com.platform.wallet.infrastructure;

import com.platform.wallet.domain.TransactionType;
import com.platform.wallet.domain.WalletTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtAsc(UUID walletId);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    /** Phase 11 — "volume financier transité" (platform KPI): every CREDIT covers both an
     *  organization deposit and a reward payout, so summing it is the total money that has ever
     *  entered the wallet system. */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t WHERE t.type = :type")
    BigDecimal sumAmountByType(@Param("type") TransactionType type);
}
