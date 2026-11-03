package com.platform.wallet.infrastructure;

import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerTypeAndOwnerId(WalletOwnerType ownerType, UUID ownerId);

    /**
     * CREDIT — unconditional, always succeeds (deposits, rewards).
     *
     * <p>Deliberately plain {@code @Modifying}, not {@code clearAutomatically = true}: clearing
     * would evict the *entire* persistence context, including entities the caller loaded earlier
     * in the same transaction (e.g. {@code CampaignService#publish}'s {@code Campaign}) — turning
     * them into silently-detached objects whose later mutations never get flushed. Instead,
     * {@code WalletService} applies the same delta to its already-loaded {@code Wallet} instance
     * in memory right after a successful CAS and detaches only that one entity — see
     * {@code WalletService#applyAndDetach}.
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.availableBalance = w.availableBalance + :amount WHERE w.id = :id")
    int creditAvailable(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /**
     * RESERVE — BR-010/DAT §25, a single conditional UPDATE (not read-then-write): moves
     * {@code amount} from available to reserved, only if enough is available. Returns 1 if the
     * reservation succeeded, 0 if the balance was insufficient.
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.availableBalance = w.availableBalance - :amount, "
            + "w.reservedBalance = w.reservedBalance + :amount "
            + "WHERE w.id = :id AND w.availableBalance >= :amount")
    int tryReserve(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /** RELEASE — the inverse of {@link #tryReserve}: moves {@code amount} back to available. */
    @Modifying
    @Query("UPDATE Wallet w SET w.availableBalance = w.availableBalance + :amount, "
            + "w.reservedBalance = w.reservedBalance - :amount "
            + "WHERE w.id = :id AND w.reservedBalance >= :amount")
    int tryRelease(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /** WITHDRAWAL (as a permanent consumption of reserved funds) — available is untouched. */
    @Modifying
    @Query("UPDATE Wallet w SET w.reservedBalance = w.reservedBalance - :amount WHERE w.id = :id AND w.reservedBalance >= :amount")
    int tryConsumeReserved(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    /** KPI counters only — never gate anything, always succeed. See {@link Wallet#bumpLifetimeEarned}. */
    @Modifying
    @Query("UPDATE Wallet w SET w.lifetimeEarned = w.lifetimeEarned + :amount WHERE w.id = :id")
    int bumpLifetimeEarned(@Param("id") UUID id, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Wallet w SET w.lifetimeWithdrawn = w.lifetimeWithdrawn + :amount WHERE w.id = :id")
    int bumpLifetimeWithdrawn(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
