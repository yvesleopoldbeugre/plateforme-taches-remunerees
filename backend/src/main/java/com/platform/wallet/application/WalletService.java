package com.platform.wallet.application;

import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ConflictException;
import com.platform.common.exception.NotFoundException;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.TransactionType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import com.platform.wallet.domain.WalletTransaction;
import com.platform.wallet.infrastructure.WalletRepository;
import com.platform.wallet.infrastructure.WalletTransactionRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the wallet/ledger core (DAT §4.2/§25, roadmap Phase 7). This module is
 * a dependency-free noyau (see {@code package-info}): it never calls into campaigns, tasks,
 * participations, organizations or users — every caller passes the owner identity and amount it
 * already has.
 *
 * <p>Every balance-mutating operation takes an {@code idempotencyKey}: the operation is looked
 * up by that key first, and a hit is treated as a successful no-op replay (not an error) rather
 * than re-applying the mutation — the normal way a caller retries after a crash or a redelivered
 * event. A genuine concurrent double-submission of the same key is the rare case and is left to
 * surface as {@code IDEMPOTENCY_KEY_CONFLICT} from the {@code wallet_transactions} unique index.
 *
 * <p>After a CAS update succeeds, the balance is never re-read with a second {@code findById}:
 * callers of {@code publish}/{@code cancel} etc. may already hold other entities loaded earlier
 * in the very same transaction (e.g. {@code CampaignService}'s {@code Campaign}), and a second
 * read risks returning the persistence context's stale copy of *this* entity unless it's evicted
 * first — which, done context-wide (e.g. {@code clearAutomatically = true}), would silently
 * detach those *other* entities too, so their later changes never flush. Instead,
 * {@link #applyAndDetach} mutates the already-loaded {@link Wallet} instance with the exact same
 * delta the SQL just applied, then detaches only that one instance — surgical, not context-wide.
 */
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final EntityManager entityManager;

    public WalletService(WalletRepository walletRepository, WalletTransactionRepository transactionRepository,
                          EntityManager entityManager) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.entityManager = entityManager;
    }

    private Wallet applyAndDetach(Wallet wallet, java.util.function.Consumer<Wallet> mutation) {
        mutation.accept(wallet);
        entityManager.detach(wallet);
        return wallet;
    }

    @Transactional
    public Wallet getOrCreateWallet(WalletOwnerType ownerType, UUID ownerId) {
        return walletRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId)
                .orElseGet(() -> walletRepository.saveAndFlush(new Wallet(ownerType, ownerId)));
    }

    public Wallet getWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException("WALLET_NOT_FOUND", "Portefeuille introuvable."));
    }

    public List<WalletTransaction> listTransactions(UUID walletId) {
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }

    /** Deposit / reward payout — always succeeds, increases {@code available_balance}. */
    @Transactional
    public Wallet credit(WalletOwnerType ownerType, UUID ownerId, BigDecimal amount, ReferenceType referenceType,
                          UUID referenceId, String idempotencyKey) {
        requirePositive(amount);
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> getWallet(tx.getWalletId()))
                .orElseGet(() -> {
                    Wallet wallet = getOrCreateWallet(ownerType, ownerId);
                    walletRepository.creditAvailable(wallet.getId(), amount);
                    boolean isReward = referenceType == ReferenceType.REWARD;
                    if (isReward) {
                        walletRepository.bumpLifetimeEarned(wallet.getId(), amount);
                    }
                    wallet = applyAndDetach(wallet, w -> {
                        w.applyCredit(amount);
                        if (isReward) {
                            w.bumpLifetimeEarned(amount);
                        }
                    });
                    recordTransaction(wallet, TransactionType.CREDIT, amount, wallet.getAvailableBalance(),
                            referenceType, referenceId, idempotencyKey);
                    return wallet;
                });
    }

    /** BR-010 — reserves {@code amount} out of available_balance; throws if insufficient. */
    @Transactional
    public Wallet reserve(WalletOwnerType ownerType, UUID ownerId, BigDecimal amount, ReferenceType referenceType,
                           UUID referenceId, String idempotencyKey) {
        requirePositive(amount);
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> getWallet(tx.getWalletId()))
                .orElseGet(() -> {
                    Wallet wallet = getOrCreateWallet(ownerType, ownerId);
                    if (walletRepository.tryReserve(wallet.getId(), amount) == 0) {
                        throw new BusinessException("INSUFFICIENT_BALANCE", HttpStatus.CONFLICT,
                                "Solde disponible insuffisant pour cette réservation (BR-010).");
                    }
                    wallet = applyAndDetach(wallet, w -> w.applyReserve(amount));
                    recordTransaction(wallet, TransactionType.RESERVE, amount.negate(), wallet.getAvailableBalance(),
                            referenceType, referenceId, idempotencyKey);
                    return wallet;
                });
    }

    /** Returns a previously reserved amount to available_balance (campaign cancellation, BR-011). */
    @Transactional
    public Wallet release(WalletOwnerType ownerType, UUID ownerId, BigDecimal amount, ReferenceType referenceType,
                           UUID referenceId, String idempotencyKey) {
        requirePositive(amount);
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> getWallet(tx.getWalletId()))
                .orElseGet(() -> {
                    Wallet wallet = getOrCreateWallet(ownerType, ownerId);
                    if (walletRepository.tryRelease(wallet.getId(), amount) == 0) {
                        throw new ConflictException("RELEASE_EXCEEDS_RESERVED",
                                "Le montant à libérer dépasse le solde réservé du portefeuille.");
                    }
                    wallet = applyAndDetach(wallet, w -> w.applyRelease(amount));
                    recordTransaction(wallet, TransactionType.RELEASE, amount, wallet.getAvailableBalance(),
                            referenceType, referenceId, idempotencyKey);
                    return wallet;
                });
    }

    /** Permanently consumes previously reserved funds (a reward actually paid out of a campaign's budget). */
    @Transactional
    public Wallet consumeReserved(WalletOwnerType ownerType, UUID ownerId, BigDecimal amount, ReferenceType referenceType,
                                   UUID referenceId, String idempotencyKey) {
        requirePositive(amount);
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> getWallet(tx.getWalletId()))
                .orElseGet(() -> {
                    Wallet wallet = getOrCreateWallet(ownerType, ownerId);
                    if (walletRepository.tryConsumeReserved(wallet.getId(), amount) == 0) {
                        throw new ConflictException("CONSUME_EXCEEDS_RESERVED",
                                "Le montant à consommer dépasse le solde réservé du portefeuille.");
                    }
                    boolean isWithdrawal = referenceType == ReferenceType.WITHDRAWAL;
                    if (isWithdrawal) {
                        walletRepository.bumpLifetimeWithdrawn(wallet.getId(), amount);
                    }
                    wallet = applyAndDetach(wallet, w -> {
                        w.applyConsumeReserved(amount);
                        if (isWithdrawal) {
                            w.bumpLifetimeWithdrawn(amount);
                        }
                    });
                    recordTransaction(wallet, TransactionType.WITHDRAWAL, amount.negate(), wallet.getReservedBalance(),
                            referenceType, referenceId, idempotencyKey);
                    return wallet;
                });
    }

    private void recordTransaction(Wallet wallet, TransactionType type, BigDecimal amount, BigDecimal balanceAfter,
                                     ReferenceType referenceType, UUID referenceId, String idempotencyKey) {
        try {
            transactionRepository.saveAndFlush(new WalletTransaction(
                    wallet.getId(), type, amount, balanceAfter, referenceType, referenceId, idempotencyKey));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("IDEMPOTENCY_KEY_CONFLICT",
                    "Cette opération a déjà été traitée avec cette clé d'idempotence.");
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_AMOUNT", HttpStatus.BAD_REQUEST, "Le montant doit être strictement positif.");
        }
    }

    /**
     * Replays a wallet's full ledger from scratch and recomputes what {@code available_balance}
     * and {@code reserved_balance} should be — the Phase 7 roadmap milestone ("Invariant solde =
     * somme ledger vérifié par test") is this method's result matching the live {@link Wallet}
     * row after any sequence of operations. See {@link TransactionType} for the per-type rules
     * this mirrors exactly.
     */
    public ReconstructedBalances reconstructBalances(UUID walletId) {
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (WalletTransaction tx : transactionRepository.findByWalletIdOrderByCreatedAtAsc(walletId)) {
            switch (tx.getType()) {
                case CREDIT, DEBIT, FEE, REVERSAL, ADJUSTMENT -> available = available.add(tx.getAmount());
                case RESERVE, RELEASE -> {
                    available = available.add(tx.getAmount());
                    reserved = reserved.subtract(tx.getAmount());
                }
                case WITHDRAWAL -> reserved = reserved.add(tx.getAmount());
            }
        }
        return new ReconstructedBalances(available, reserved);
    }

    public record ReconstructedBalances(BigDecimal availableBalance, BigDecimal reservedBalance) {
    }
}
