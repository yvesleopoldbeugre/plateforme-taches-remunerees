package com.platform.users.domain;

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

/** Maps to {@code users} (DAT §4.2) — identity and credentials, not profile data. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", length = 32)
    private PlatformRole platformRole;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "mfa_secret", length = 64)
    private String mfaSecret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected User() {
        // JPA
    }

    public User(String email, String phone, String passwordHash) {
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isPhoneVerified() {
        return phoneVerifiedAt != null;
    }

    public void markEmailVerified() {
        this.emailVerifiedAt = Instant.now();
        activateIfFullyVerified();
    }

    public void markPhoneVerified() {
        this.phoneVerifiedAt = Instant.now();
        activateIfFullyVerified();
    }

    private void activateIfFullyVerified() {
        if (status == UserStatus.PENDING_VERIFICATION && isEmailVerified() && isPhoneVerified()) {
            this.status = UserStatus.ACTIVE;
        }
        this.updatedAt = Instant.now();
    }

    /** UC-21 — admin action. Refuses on an already-{@code BANNED} account: that's a more severe
     *  state than a suspension, and downgrading it silently would hide why it was banned. */
    public void suspend() {
        if (status == UserStatus.BANNED) {
            throw new ConflictException("USER_BANNED", "Ce compte est banni, pas seulement suspendu.");
        }
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    /** From SUSPENDED or BANNED back to ACTIVE. Doesn't re-check email/phone verification —
     *  a user reaching SUSPENDED/BANNED from anywhere else than ACTIVE is an edge case this MVP
     *  accepts rather than tracks precisely. */
    public void reactivate() {
        if (status != UserStatus.SUSPENDED && status != UserStatus.BANNED) {
            throw new ConflictException("USER_NOT_SUSPENDED",
                    "Ce compte n'est ni suspendu ni banni (statut actuel : " + status + ").");
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void ban() {
        this.status = UserStatus.BANNED;
        this.updatedAt = Instant.now();
    }

    /** Admin bootstrap/promotion (UC absent from the CDCF's own list — every phase since Phase 2
     *  flagged "bootstrap d'un premier compte PLATFORM_ADMIN" as deferred; closed in Phase 9). */
    public void grantPlatformRole(PlatformRole role) {
        this.platformRole = role;
        this.updatedAt = Instant.now();
    }

    /** CDCT §8 — TOTP. The secret is stored but {@code mfaEnabled} stays false until a code is
     *  actually confirmed ({@link #confirmMfaEnabled()}) — a user who starts setup and never
     *  finishes it is not protected by anything, and must not be made to look like they are. */
    public void beginMfaEnrollment(String secret) {
        this.mfaSecret = secret;
        this.updatedAt = Instant.now();
    }

    public void confirmMfaEnabled() {
        if (mfaSecret == null) {
            throw new ConflictException("MFA_NOT_SETUP", "Lancez l'enrôlement MFA avant de le confirmer.");
        }
        this.mfaEnabled = true;
        this.updatedAt = Instant.now();
    }

    public void disableMfa() {
        this.mfaEnabled = false;
        this.mfaSecret = null;
        this.updatedAt = Instant.now();
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public PlatformRole getPlatformRole() {
        return platformRole;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
