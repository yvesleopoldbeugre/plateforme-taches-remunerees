package com.platform.payments.domain;

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
 * Maps to {@code payments} (DAT §4.2) — an organization's deposit into its own wallet, routed
 * through a {@link com.platform.payments.application.PaymentProvider}. {@code organizationId}
 * has a real FK (deposits are organization-only, unlike the polymorphic {@code payment_methods}).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Payment() {
        // JPA
    }

    public Payment(UUID organizationId, UUID paymentMethodId, String provider, BigDecimal amount, String idempotencyKey) {
        this.organizationId = organizationId;
        this.paymentMethodId = paymentMethodId;
        this.provider = provider;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public void complete(String providerReference) {
        assertPending();
        this.status = PaymentStatus.COMPLETED;
        this.providerReference = providerReference;
        this.completedAt = Instant.now();
    }

    public void fail() {
        assertPending();
        this.status = PaymentStatus.FAILED;
        this.completedAt = Instant.now();
    }

    private void assertPending() {
        if (status != PaymentStatus.PENDING) {
            throw new ConflictException("PAYMENT_NOT_PENDING",
                    "Ce paiement n'est plus en attente (statut actuel : " + status + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
