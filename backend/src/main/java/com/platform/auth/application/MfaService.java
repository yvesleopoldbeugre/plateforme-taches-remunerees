package com.platform.auth.application;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.UnauthorizedException;
import com.platform.organizations.application.OrganizationService;
import com.platform.users.application.UserService;
import com.platform.users.domain.PlatformRole;
import com.platform.users.domain.User;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Orchestrates TOTP enrollment/verification (CDCT §8). Depends on {@code organizations} (one
 * new, one-directional edge: {@code auth} → {@code organizations}, no cycle since organizations
 * already depends only on {@code users}) purely to answer "is MFA mandatory for this account" —
 * the dossier makes it mandatory for {@code ORGANIZATION_OWNER}, not just the two platform roles,
 * and nothing else needs that fact.
 *
 * <p>"Mandatory" is enforced as a strong nudge, not a hard pre-auth gate: a mandatory-but-not-
 * enrolled account still logs in normally (its own existing session must keep working), with
 * {@code mfaSetupRecommended: true} in the response, and can no longer voluntarily disable MFA
 * once enrolled. A true hard gate (blocking login entirely until enrollment completes) would need
 * a restricted intermediate session state — a reasonable hardening left for a later pass, not
 * built here because it's disproportionate to what this phase's scope needs.
 */
@Service
public class MfaService {

    private static final String CHALLENGE_PREFIX = "mfa-challenge:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    private final UserService userService;
    private final OrganizationService organizationService;
    private final TotpService totpService;
    private final StringRedisTemplate redis;

    public MfaService(UserService userService, OrganizationService organizationService,
                       TotpService totpService, StringRedisTemplate redis) {
        this.userService = userService;
        this.organizationService = organizationService;
        this.totpService = totpService;
        this.redis = redis;
    }

    public boolean isMandatoryForRole(User user) {
        if (user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN || user.getPlatformRole() == PlatformRole.PLATFORM_OPERATOR) {
            return true;
        }
        return organizationService.isOwnerOfAnyActiveOrganization(user.getId());
    }

    public MfaSetup setup(UUID userId) {
        User user = userService.getById(userId);
        String secret = totpService.generateSecret();
        userService.beginMfaEnrollment(userId, secret);
        return new MfaSetup(secret, totpService.provisioningUri(secret, user.getEmail()));
    }

    public void enable(UUID userId, String code) {
        User user = userService.getById(userId);
        if (user.getMfaSecret() == null) {
            throw new ConflictException("MFA_NOT_SETUP", "Appelez /auth/mfa/setup avant d'activer le MFA.");
        }
        if (!totpService.verify(user.getMfaSecret(), code)) {
            throw new UnauthorizedException("INVALID_MFA_CODE", "Code TOTP invalide.");
        }
        userService.confirmMfaEnabled(userId);
    }

    /** Refuses when the role mandates MFA (CDCT §8) — same split as {@code UserController#suspend}:
     *  this is a self-service action, but the dossier's "obligatoire" has to mean something. */
    public void disable(UUID userId, String code) {
        User user = userService.getById(userId);
        if (!user.isMfaEnabled()) {
            throw new ConflictException("MFA_NOT_ENABLED", "Le MFA n'est pas activé sur ce compte.");
        }
        if (!totpService.verify(user.getMfaSecret(), code)) {
            throw new UnauthorizedException("INVALID_MFA_CODE", "Code TOTP invalide.");
        }
        if (isMandatoryForRole(user)) {
            throw new ConflictException("MFA_MANDATORY_FOR_ROLE",
                    "Le MFA est obligatoire pour ce rôle ou cette organisation et ne peut pas être désactivé (CDCT §8).");
        }
        userService.disableMfa(userId);
    }

    /** The brief gap between "password verified" and "TOTP verified" during login — mirrors
     *  {@code VerificationService}'s Redis-only, never-persisted token pattern. */
    public String issueLoginChallenge(UUID userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(CHALLENGE_PREFIX + token, userId.toString(), CHALLENGE_TTL);
        return token;
    }

    /**
     * Read-only on purpose: a wrong TOTP code must not burn the challenge — a user who mistypes
     * their code should be able to retry against the same challenge, not have to restart the
     * whole login. Only {@link #invalidateLoginChallenge} actually removes it, called by
     * {@code LoginService} after the code checks out.
     */
    public UUID peekLoginChallenge(String challengeToken) {
        String userId = redis.opsForValue().get(CHALLENGE_PREFIX + challengeToken);
        if (userId == null) {
            throw new UnauthorizedException("MFA_CHALLENGE_EXPIRED", "Session de vérification MFA expirée — reconnectez-vous.");
        }
        return UUID.fromString(userId);
    }

    public void invalidateLoginChallenge(String challengeToken) {
        redis.delete(CHALLENGE_PREFIX + challengeToken);
    }

    public boolean verifyLoginCode(UUID userId, String code) {
        User user = userService.getById(userId);
        return totpService.verify(user.getMfaSecret(), code);
    }

    public record MfaSetup(String secret, String provisioningUri) {
    }
}
