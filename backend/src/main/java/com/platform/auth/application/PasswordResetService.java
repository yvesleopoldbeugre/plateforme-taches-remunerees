package com.platform.auth.application;

import com.platform.common.exception.UnauthorizedException;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * "Réinitialisation de mot de passe" — deferred since Phase 2. Same Redis-only, never-persisted
 * token discipline as {@code VerificationService}'s email link (DAT §9 — "fraîcheur des preuves
 * d'identité"). {@link #requestReset} never reveals whether the email exists (CDCT §8 anti-
 * enumeration, same as {@code VerificationService#resendEmailVerification}).
 */
@Service
public class PasswordResetService {

    private static final String TOKEN_PREFIX = "password-reset:";
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationNotifier notifier;
    private final RefreshTokenService refreshTokenService;

    public PasswordResetService(StringRedisTemplate redis, UserService userService, PasswordEncoder passwordEncoder,
                                 VerificationNotifier notifier, RefreshTokenService refreshTokenService) {
        this.redis = redis;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.refreshTokenService = refreshTokenService;
    }

    public void requestReset(String email) {
        userService.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            redis.opsForValue().set(TOKEN_PREFIX + token, user.getId().toString(), TOKEN_TTL);
            notifier.sendPasswordResetEmail(user.getEmail(), token);
        });
        // Always returns silently otherwise — no account-existence leak.
    }

    /** Also revokes every existing session (CDCT §8): a password reset is exactly the moment an
     *  attacker's already-stolen refresh token, if any, must stop working too. */
    public void confirmReset(String token, String newPassword) {
        String key = TOKEN_PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new UnauthorizedException("INVALID_TOKEN", "Lien de réinitialisation invalide ou expiré.");
        }
        redis.delete(key);
        UUID id = UUID.fromString(userId);
        User user = userService.updatePasswordHash(id, passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllForUser(user.getId());
    }
}
