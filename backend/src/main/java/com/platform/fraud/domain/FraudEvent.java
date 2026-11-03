package com.platform.fraud.domain;

import com.platform.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps to {@code fraud_events} (DAT §4.2, UC-22). {@code userId}/{@code campaignId} are nullable
 * FKs (a future detector might flag neither, or only one) — this phase's one detector
 * (BR-051/duplicate submissions) always sets both.
 */
@Entity
@Table(name = "fraud_events")
public class FraudEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private FraudEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private FraudRiskLevel riskLevel;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "campaign_id")
    private UUID campaignId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FraudEventStatus status = FraudEventStatus.OPEN;

    @Column(name = "admin_decision")
    private String adminDecision;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected FraudEvent() {
        // JPA
    }

    public FraudEvent(FraudEventType type, FraudRiskLevel riskLevel, UUID userId, UUID campaignId, String reason) {
        this.type = type;
        this.riskLevel = riskLevel;
        this.userId = userId;
        this.campaignId = campaignId;
        this.reason = reason;
    }

    /** "Escalade" in the CDCF's wording — looked at, no final call yet. Stays reachable from OPEN only. */
    public void markReviewed(UUID reviewerId, String note) {
        assertOpen();
        this.status = FraudEventStatus.REVIEWED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.adminDecision = note;
    }

    /** "Classement sans suite." */
    public void dismiss(UUID reviewerId, String note) {
        assertOpenOrReviewed();
        this.status = FraudEventStatus.DISMISSED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.adminDecision = note;
    }

    /** "Suspension de compte" — {@code FraudService} is what actually suspends the user. */
    public void confirm(UUID reviewerId, String note) {
        assertOpenOrReviewed();
        this.status = FraudEventStatus.CONFIRMED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.adminDecision = note;
    }

    private void assertOpen() {
        if (status != FraudEventStatus.OPEN) {
            throw new ConflictException("FRAUD_EVENT_NOT_OPEN",
                    "Cet événement n'est plus ouvert (statut actuel : " + status + ").");
        }
    }

    private void assertOpenOrReviewed() {
        if (status != FraudEventStatus.OPEN && status != FraudEventStatus.REVIEWED) {
            throw new ConflictException("FRAUD_EVENT_ALREADY_DECIDED",
                    "Une décision finale a déjà été prise sur cet événement (statut actuel : " + status + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public FraudEventType getType() {
        return type;
    }

    public FraudRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCampaignId() {
        return campaignId;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public String getReason() {
        return reason;
    }

    public FraudEventStatus getStatus() {
        return status;
    }

    public String getAdminDecision() {
        return adminDecision;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
