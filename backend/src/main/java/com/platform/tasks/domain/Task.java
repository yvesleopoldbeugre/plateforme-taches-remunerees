package com.platform.tasks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code tasks} (DAT §4.2). {@code type} and {@code expectedProofType} are plain
 * strings validated against {@link TaskTypes}/{@link ProofTypes} at the service layer, not
 * JPA enums — see {@link TaskTypes} for why.
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String instructions;

    @Column(name = "reward_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal rewardAmount;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "current_participants", nullable = false)
    private int currentParticipants = 0;

    @Column(name = "expected_proof_type", nullable = false, length = 32)
    private String expectedProofType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auto_validation_rules")
    private String autoValidationRules;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status = TaskStatus.ACTIVE;

    @Column(name = "reservation_ttl_minutes", nullable = false)
    private int reservationTtlMinutes = 1440;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Task() {
        // JPA
    }

    public Task(UUID campaignId, String type, String title, String instructions, BigDecimal rewardAmount,
                int maxParticipants, String expectedProofType, String autoValidationRules, Integer reservationTtlMinutes) {
        this.campaignId = campaignId;
        this.type = type;
        this.title = title;
        this.instructions = instructions;
        this.rewardAmount = rewardAmount;
        this.maxParticipants = maxParticipants;
        this.expectedProofType = expectedProofType;
        this.autoValidationRules = autoValidationRules;
        if (reservationTtlMinutes != null) {
            this.reservationTtlMinutes = reservationTtlMinutes;
        }
    }

    public BigDecimal totalBudget() {
        return rewardAmount.multiply(BigDecimal.valueOf(maxParticipants));
    }

    public UUID getId() {
        return id;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getInstructions() {
        return instructions;
    }

    public BigDecimal getRewardAmount() {
        return rewardAmount;
    }

    public int getMaxParticipants() {
        return maxParticipants;
    }

    public int getCurrentParticipants() {
        return currentParticipants;
    }

    public String getExpectedProofType() {
        return expectedProofType;
    }

    public String getAutoValidationRules() {
        return autoValidationRules;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getReservationTtlMinutes() {
        return reservationTtlMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
