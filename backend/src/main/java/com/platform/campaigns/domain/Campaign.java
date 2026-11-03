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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
