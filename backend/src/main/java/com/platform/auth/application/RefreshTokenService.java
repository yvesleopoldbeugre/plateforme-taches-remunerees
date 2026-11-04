package com.platform.auth.application;

import com.platform.common.exception.UnauthorizedException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Refresh tokens are opaque, rotated on every use, and stored in Redis so a single one can be
 * revoked without invalidating the user's password (CDCT §8 — "refresh token rotatif stocké en
 * Redis, révocable"). Phase 12 adds a reverse index ({@code USER_INDEX_PREFIX}) so every token
 * for a user can be found and revoked together ({@link #revokeAllForUser}) — "révocation de
 * toutes les sessions d'un coup", deferred since Phase 2. The index's own TTL is refreshed on
 * every {@link #issue}, so it stays alive at least as long as its newest member; it may outlive
 * an individual already-expired token by a little, which {@link #revokeAllForUser} tolerates
 * (deleting an already-gone key is a no-op).
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";
    private static final String USER_INDEX_PREFIX = "refresh-tokens-by-user:";

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
        String indexKey = USER_INDEX_PREFIX + userId;
        redis.opsForSet().add(indexKey, token);
        redis.expire(indexKey, ttl);
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
        redis.opsForSet().remove(USER_INDEX_PREFIX + userId, token);
        String newToken = issue(UUID.fromString(userId));
        return new RotatedToken(UUID.fromString(userId), newToken);
    }

    public void revoke(String token) {
        String userId = redis.opsForValue().get(KEY_PREFIX + token);
        redis.delete(KEY_PREFIX + token);
        if (userId != null) {
            redis.opsForSet().remove(USER_INDEX_PREFIX + userId, token);
        }
    }

    /** Returns the number of sessions actually revoked. */
    public int revokeAllForUser(UUID userId) {
        String indexKey = USER_INDEX_PREFIX + userId;
        Set<String> tokens = redis.opsForSet().members(indexKey);
        if (tokens == null || tokens.isEmpty()) {
            return 0;
        }
        for (String token : tokens) {
            redis.delete(KEY_PREFIX + token);
        }
        redis.delete(indexKey);
        return tokens.size();
    }

    public record RotatedToken(UUID userId, String token) {
    }
}
