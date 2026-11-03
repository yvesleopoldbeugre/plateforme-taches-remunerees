package com.platform.auth.application;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.UnauthorizedException;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Email-link and phone-OTP verification (BR-001, UC-01). Tokens/codes live only in Redis —
 * short-lived, never persisted to the primary database (DAT §7.2 / §9 "idempotence &
 * fraîcheur des preuves d'identité").
 */
@Service
public class VerificationService {

    private static final String EMAIL_TOKEN_PREFIX = "verify:email:";
    private static final String PHONE_OTP_PREFIX = "verify:phone:";
    private static final String PHONE_ATTEMPTS_SUFFIX = ":attempts";
    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration PHONE_OTP_TTL = Duration.ofMinutes(10);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final VerificationNotifier notifier;
    private final SecureRandom random = new SecureRandom();

    public VerificationService(StringRedisTemplate redis, UserService userService, VerificationNotifier notifier) {
        this.redis = redis;
        this.userService = userService;
        this.notifier = notifier;
    }

    public void issueEmailVerification(User user) {
        String token = UUID.randomUUID().toString();
        redis.opsForValue().set(EMAIL_TOKEN_PREFIX + token, user.getId().toString(), EMAIL_TOKEN_TTL);
        notifier.sendEmailVerification(user.getEmail(), token);
    }

    public void resendEmailVerification(String email) {
        userService.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueEmailVerification);
        // Always returns silently otherwise, to avoid leaking account existence.
    }

    public void verifyEmail(String token) {
        String key = EMAIL_TOKEN_PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new UnauthorizedException("INVALID_TOKEN", "Lien de vérification invalide ou expiré.");
        }
        redis.delete(key);
        userService.markEmailVerified(UUID.fromString(userId));
    }

    public void issuePhoneOtp(User user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        String userId = user.getId().toString();
        redis.opsForValue().set(PHONE_OTP_PREFIX + userId, code, PHONE_OTP_TTL);
        redis.delete(PHONE_OTP_PREFIX + userId + PHONE_ATTEMPTS_SUFFIX);
        notifier.sendPhoneOtp(user.getPhone(), code);
    }

    public void verifyPhoneOtp(UUID userId, String code) {
        String codeKey = PHONE_OTP_PREFIX + userId;
        String attemptsKey = codeKey + PHONE_ATTEMPTS_SUFFIX;
        String expected = redis.opsForValue().get(codeKey);

        if (expected == null) {
            throw new UnauthorizedException("OTP_EXPIRED", "Code expiré — demandez-en un nouveau.");
        }
        Long attempts = redis.opsForValue().increment(attemptsKey);
        redis.expire(attemptsKey, PHONE_OTP_TTL);
        if (attempts != null && attempts > MAX_OTP_ATTEMPTS) {
            redis.delete(codeKey);
            throw new ConflictException("TOO_MANY_ATTEMPTS", "Trop de tentatives — demandez un nouveau code.");
        }
        if (!expected.equals(code)) {
            throw new UnauthorizedException("INVALID_OTP", "Code incorrect.");
        }
        redis.delete(codeKey);
        redis.delete(attemptsKey);
        userService.markPhoneVerified(userId);
    }
}
