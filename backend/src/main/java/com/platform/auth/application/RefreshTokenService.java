package com.platform.auth.application;

import com.platform.common.exception.UnauthorizedException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens are opaque, rotated on every use, and stored in Redis so a single one can be
 * revoked without invalidating the user's password (CDCT §8 — "refresh token rotatif stocké en
 * Redis, révocable").
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenService(StringRedisTemplate redis,
                                @Value("${jwt.refresh-token-ttl-days:30}") long ttlDays) {
        this.redis = redis;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public String issue(UUID userId) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(KEY_PREFIX + token, userId.toString(), ttl);
        return token;
    }

    /** Validates {@code token}, revokes it, and issues a fresh one for the same user. */
    public RotatedToken rotate(String token) {
        String key = KEY_PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Jeton de rafraîchissement invalide ou expiré.");
        }
        redis.delete(key);
        String newToken = issue(UUID.fromString(userId));
        return new RotatedToken(UUID.fromString(userId), newToken);
    }

    public void revoke(String token) {
        redis.delete(KEY_PREFIX + token);
    }

    public record RotatedToken(UUID userId, String token) {
    }
}
