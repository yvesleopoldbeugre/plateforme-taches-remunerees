package com.platform.auth.application;

import com.platform.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates the short-lived JWT access token (CDCT §8 — 15 min by default). The
 * refresh token itself is opaque and handled separately by {@link RefreshTokenService}.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-ttl-minutes:15}") long accessTokenTtlMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 256 bits (32 bytes) long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public String generateAccessToken(UUID userId, List<String> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("authorities", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipal validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            @SuppressWarnings("unchecked")
            List<String> authorities = claims.get("authorities", List.class);
            return new JwtPrincipal(userId, authorities == null ? List.of() : authorities);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("TOKEN_EXPIRED", "Le jeton d'accès a expiré.");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("INVALID_TOKEN", "Jeton d'accès invalide.");
        }
    }

    public record JwtPrincipal(UUID userId, List<String> authorities) {
    }
}
