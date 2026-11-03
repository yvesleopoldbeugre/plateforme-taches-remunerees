package com.platform.wallet.domain;

import com.platform.common.exception.ConflictException;
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
import java.util.UUID;

/**
 * Maps to {@code rewards} (DAT §4.2) — one row per approved participation, created PENDING by
 * {@code SubmissionApprovedListener} and marked CREDITED once the user's wallet has actually
 * been credited (same transaction, so the two are never out of sync).
 */
@Entity
@Table(name = "rewards")
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "participation_id", nullable = false, unique = true)
    private UUID participationId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RewardStatus status = RewardStatus.PENDING;

    @Column(name = "credited_at")
    private Instant creditedAt;

    protected Reward() {
        // JPA
    }

    public Reward(UUID participationId, BigDecimal amount, String currency) {
        this.participationId = participationId;
        this.amount = amount;
        this.currency = currency;
    }

    public void markCredited() {
        if (status != RewardStatus.PENDING) {
            throw new ConflictException("REWARD_NOT_PENDING",
                    "Cette récompense n'est plus en attente (statut actuel : " + status + ").");
        }
        this.status = RewardStatus.CREDITED;
        this.creditedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getParticipationId() {
        return participationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public RewardStatus getStatus() {
        return status;
    }

    public Instant getCreditedAt() {
        return creditedAt;
    }
}
