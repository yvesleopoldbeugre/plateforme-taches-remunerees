package com.platform.wallet.infrastructure;

import com.platform.wallet.domain.WalletTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtAsc(UUID walletId);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
