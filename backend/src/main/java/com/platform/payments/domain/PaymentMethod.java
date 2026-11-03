package com.platform.payments.domain;

import com.platform.wallet.domain.WalletOwnerType;
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
 * Maps to {@code payment_methods} (DAT §4.2) — a saved payout/cash-in destination for a user or
 * an organization. Polymorphic owner (reuses {@link WalletOwnerType}, same reasoning as
 * {@code wallets}: no FK to {@code users}/{@code organizations}).
 */
@Entity
@Table(name = "payment_methods")
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private WalletOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "masked_identifier", nullable = false, length = 64)
    private String maskedIdentifier;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentMethodStatus status = PaymentMethodStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PaymentMethod() {
        // JPA
    }

    public PaymentMethod(WalletOwnerType ownerType, UUID ownerId, String provider, String type,
                          String maskedIdentifier, boolean isDefault) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.provider = provider;
        this.type = type;
        this.maskedIdentifier = maskedIdentifier;
        this.isDefault = isDefault;
    }

    public void deactivate() {
        this.status = PaymentMethodStatus.INACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public WalletOwnerType getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getProvider() {
        return provider;
    }

    public String getType() {
        return type;
    }

    public String getMaskedIdentifier() {
        return maskedIdentifier;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public PaymentMethodStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
