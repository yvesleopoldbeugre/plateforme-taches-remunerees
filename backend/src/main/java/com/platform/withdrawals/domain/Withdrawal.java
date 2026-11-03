package com.platform.withdrawals.domain;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.ForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Maps to {@code withdrawals} (DAT §4.2) — a user's request to move money out of the platform.
 * Lifecycle: REQUESTED → (UNDER_REVIEW →) APPROVED → PROCESSING → COMPLETED/FAILED, or
 * REQUESTED/UNDER_REVIEW → REJECTED/CANCELLED. {@code amount} is reserved on the user's wallet
 * the moment the row is created (BR-043/DAT §8.3 — "débit immédiat vers reserved_balance dès la
 * demande"); {@code fees} is deducted from it, {@code net_amount} is what the provider actually
 * pays out.
 */
@Entity
@Table(name = "withdrawals")
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "payment_method_id", nullable = false)
    private UUID paymentMethodId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal fees;

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WithdrawalStatus status = WithdrawalStatus.REQUESTED;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Withdrawal() {
        // JPA
    }

    public Withdrawal(UUID userId, UUID walletId, UUID paymentMethodId, BigDecimal amount, BigDecimal fees,
                       BigDecimal netAmount, String idempotencyKey) {
        this.userId = userId;
        this.walletId = walletId;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.fees = fees;
        this.netAmount = netAmount;
        this.idempotencyKey = idempotencyKey;
    }

    public void assertOwnedBy(UUID requesterId) {
        if (!userId.equals(requesterId)) {
            throw new ForbiddenException("NOT_WITHDRAWAL_OWNER", "Ce retrait n'appartient pas à cet utilisateur.");
        }
    }

    public void markUnderReview() {
        assertStatus(WithdrawalStatus.REQUESTED);
        this.status = WithdrawalStatus.UNDER_REVIEW;
    }

    public void approve() {
        assertStatus(WithdrawalStatus.REQUESTED, WithdrawalStatus.UNDER_REVIEW);
        this.status = WithdrawalStatus.APPROVED;
    }

    public void reject(String reason) {
        assertStatus(WithdrawalStatus.REQUESTED, WithdrawalStatus.UNDER_REVIEW);
        this.status = WithdrawalStatus.REJECTED;
        this.rejectionReason = reason;
        this.processedAt = Instant.now();
    }

    public void cancel() {
        assertStatus(WithdrawalStatus.REQUESTED, WithdrawalStatus.UNDER_REVIEW);
        this.status = WithdrawalStatus.CANCELLED;
        this.processedAt = Instant.now();
    }

    public void startProcessing() {
        assertStatus(WithdrawalStatus.APPROVED);
        this.status = WithdrawalStatus.PROCESSING;
    }

    public void complete() {
        assertStatus(WithdrawalStatus.PROCESSING);
        this.status = WithdrawalStatus.COMPLETED;
        this.processedAt = Instant.now();
    }

    public void fail(String reason) {
        assertStatus(WithdrawalStatus.PROCESSING);
        this.status = WithdrawalStatus.FAILED;
        this.rejectionReason = reason;
        this.processedAt = Instant.now();
    }

    private void assertStatus(WithdrawalStatus... allowed) {
        if (Arrays.stream(allowed).noneMatch(s -> s == status)) {
            throw new ConflictException("WITHDRAWAL_INVALID_STATUS",
                    "Cette opération n'est pas possible depuis le statut actuel (" + status + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
