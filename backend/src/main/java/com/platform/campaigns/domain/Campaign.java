package com.platform.campaigns.domain;

import com.platform.common.exception.ConflictException;
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

/** Maps to {@code campaigns} (DAT §4.2). Lifecycle per the state machine in DAT §10.1. */
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(nullable = false, length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "budget_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal budgetTotal = BigDecimal.ZERO;

    @Column(name = "budget_reserved", nullable = false, precision = 14, scale = 2)
    private BigDecimal budgetReserved = BigDecimal.ZERO;

    @Column(name = "budget_consumed", nullable = false, precision = 14, scale = 2)
    private BigDecimal budgetConsumed = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "XOF";

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Campaign() {
        // JPA
    }

    public Campaign(UUID organizationId, String title, String description, String category,
                     Instant startDate, Instant endDate, UUID createdBy) {
        this.organizationId = organizationId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
    }

    public void setBudgetTotal(BigDecimal budgetTotal) {
        this.budgetTotal = budgetTotal;
        this.updatedAt = Instant.now();
    }

    /** DRAFT is the only status in which tasks and eligibility criteria may still change. */
    public void assertEditable() {
        if (status != CampaignStatus.DRAFT) {
            throw new ConflictException("CAMPAIGN_NOT_EDITABLE",
                    "La campagne doit être en brouillon pour être modifiée (statut actuel : " + status + ").");
        }
    }

    public void submitForReview() {
        assertEditable();
        this.status = CampaignStatus.PENDING_REVIEW;
        this.updatedAt = Instant.now();
    }

    private void assertPendingReview() {
        if (status != CampaignStatus.PENDING_REVIEW) {
            throw new ConflictException("CAMPAIGN_NOT_PENDING_REVIEW",
                    "La campagne doit être en attente de revue (statut actuel : " + status + ").");
        }
    }

    /**
     * BR-010 (budget_total ≤ solde disponible de l'organisation) is enforced by
     * {@code CampaignService#publish} via {@code WalletService#reserve} *before* this is called
     * — this method only records that the reservation already happened.
     */
    public void publish() {
        assertPendingReview();
        this.status = CampaignStatus.PUBLISHED;
        this.budgetReserved = this.budgetTotal;
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        assertPendingReview();
        this.status = CampaignStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = Instant.now();
    }

    public void returnToDraft() {
        if (status != CampaignStatus.REJECTED) {
            throw new ConflictException("CAMPAIGN_NOT_REJECTED",
                    "Seule une campagne rejetée peut repasser en brouillon (statut actuel : " + status + ").");
        }
        this.status = CampaignStatus.DRAFT;
        this.updatedAt = Instant.now();
    }

    /**
     * BR-030 (bookkeeping side) — called when a submission under this campaign is approved: the
     * reward amount moves from "reserved" to "consumed". Does not touch the wallet itself (that
     * is {@code WalletService#consumeReserved}, driven by the same approval) — this is purely the
     * campaign's own budget view.
     */
    public void consumeBudget(BigDecimal amount) {
        this.budgetConsumed = this.budgetConsumed.add(amount);
        this.updatedAt = Instant.now();
    }

    /** The unconsumed portion of what was reserved — what {@code cancel()} returns to the wallet. */
    public BigDecimal unconsumedReservedBudget() {
        return budgetReserved.subtract(budgetConsumed);
    }

    /**
     * BR-011 — cancellation is allowed from any pre-terminal status; a campaign that was never
     * published (still DRAFT/PENDING_REVIEW) has nothing reserved to release. Campaigns already
     * COMPLETED/EXPIRED/CANCELLED/REJECTED are terminal and cannot be cancelled again.
     */
    public void cancel() {
        if (status == CampaignStatus.COMPLETED || status == CampaignStatus.EXPIRED
                || status == CampaignStatus.CANCELLED || status == CampaignStatus.REJECTED) {
            throw new ConflictException("CAMPAIGN_NOT_CANCELLABLE",
                    "Cette campagne ne peut plus être annulée (statut actuel : " + status + ").");
        }
        this.status = CampaignStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public BigDecimal getBudgetTotal() {
        return budgetTotal;
    }

    public BigDecimal getBudgetReserved() {
        return budgetReserved;
    }

    public BigDecimal getBudgetConsumed() {
        return budgetConsumed;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
