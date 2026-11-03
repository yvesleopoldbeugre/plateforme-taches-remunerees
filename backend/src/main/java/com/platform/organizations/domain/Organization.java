package com.platform.organizations.domain;

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

/** Maps to {@code organizations} (DAT §4.2). */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    @Column(nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationStatus status = OrganizationStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus = KycStatus.NOT_SUBMITTED;

    @Column(name = "kyc_rejection_reason")
    private String kycRejectionReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Organization() {
        // JPA
    }

    public Organization(String name, String legalName, String country, UUID createdBy) {
        this.name = name;
        this.legalName = legalName;
        this.country = country;
        this.createdBy = createdBy;
    }

    public boolean isSuspended() {
        return status == OrganizationStatus.SUSPENDED;
    }

    /**
     * UC-21/US-014 — admin action, closing the gap flagged since Phase 3 ("la vérification admin
     * ne conditionnera que la publication de campagnes... cette vérification elle-même n'est pas
     * encore implémentée"). {@code CampaignService#publish} now actually checks this (ACTIVE) —
     * see that method's comment for why it was always meant to.
     */
    public void verify() {
        if (status == OrganizationStatus.SUSPENDED) {
            throw new ConflictException("ORGANIZATION_SUSPENDED", "Réactivez d'abord l'organisation avant de la vérifier.");
        }
        this.status = OrganizationStatus.ACTIVE;
        this.kycStatus = KycStatus.VERIFIED;
        this.kycRejectionReason = null;
    }

    /** Only the KYC side moves — BR-003's PENDING_VERIFICATION exception still lets the owner
     *  manage members and fix things up for a resubmission; only {@code publish} stays blocked. */
    public void rejectVerification(String reason) {
        this.kycStatus = KycStatus.REJECTED;
        this.kycRejectionReason = reason;
    }

    public void suspend() {
        this.status = OrganizationStatus.SUSPENDED;
    }

    /** Falls back to the status the KYC decision already implies, not unconditionally ACTIVE —
     *  a never-verified (or rejected) organization coming out of suspension is still unverified. */
    public void reactivate() {
        if (status != OrganizationStatus.SUSPENDED) {
            throw new ConflictException("ORGANIZATION_NOT_SUSPENDED", "Cette organisation n'est pas suspendue.");
        }
        this.status = kycStatus == KycStatus.VERIFIED ? OrganizationStatus.ACTIVE : OrganizationStatus.PENDING_VERIFICATION;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getCountry() {
        return country;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public String getKycRejectionReason() {
        return kycRejectionReason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
