package com.platform.wallet.domain;

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

/**
 * Maps to {@code wallets} (DAT §4.2) — one row per (owner_type, owner_id), created lazily on
 * first use by {@code WalletService#getOrCreateWallet}. No FK to users/organizations: the owner
 * is polymorphic, which is also why this module ({@code wallet}) can stay a dependency-free
 * noyau — see {@code package-info}.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private WalletOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "pending_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(name = "reserved_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Column(name = "lifetime_earned", nullable = false, precision = 14, scale = 2)
    private BigDecimal lifetimeEarned = BigDecimal.ZERO;

    @Column(name = "lifetime_withdrawn", nullable = false, precision = 14, scale = 2)
    private BigDecimal lifetimeWithdrawn = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "XOF";

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Wallet() {
        // JPA
    }

    public Wallet(WalletOwnerType ownerType, UUID ownerId) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
    }

    /**
     * These four mirror exactly what {@code WalletRepository}'s CAS updates do in SQL — called
     * by {@code WalletService} right after that SQL succeeds, to keep this in-memory instance in
     * sync without a second read. They never check invariants themselves (the CAS's
     * {@code WHERE} clause already guaranteed the operation was valid before either ran).
     */
    public void applyCredit(BigDecimal amount) {
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void applyReserve(BigDecimal amount) {
        this.availableBalance = this.availableBalance.subtract(amount);
        this.reservedBalance = this.reservedBalance.add(amount);
    }

    public void applyRelease(BigDecimal amount) {
        this.availableBalance = this.availableBalance.add(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
    }

    public void applyConsumeReserved(BigDecimal amount) {
        this.reservedBalance = this.reservedBalance.subtract(amount);
    }

    /** KPI counters (CDCF §15 — "revenu total" / retraits cumulés) ; never decrease. */
    public void bumpLifetimeEarned(BigDecimal amount) {
        this.lifetimeEarned = this.lifetimeEarned.add(amount);
    }

    public void bumpLifetimeWithdrawn(BigDecimal amount) {
        this.lifetimeWithdrawn = this.lifetimeWithdrawn.add(amount);
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

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getPendingBalance() {
        return pendingBalance;
    }

    public BigDecimal getReservedBalance() {
        return reservedBalance;
    }

    public BigDecimal getLifetimeEarned() {
        return lifetimeEarned;
    }

    public BigDecimal getLifetimeWithdrawn() {
        return lifetimeWithdrawn;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
