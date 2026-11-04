package com.platform.auth.api.dto;

/** Returned by {@code POST /auth/login} instead of {@link TokenResponse} when the account has
 * MFA enabled — the client must call {@code POST /auth/mfa/login-verify} with this token + a
 * TOTP code to actually get a session. */
public record MfaChallengeResponse(boolean mfaRequired, String mfaChallengeToken) {
}
