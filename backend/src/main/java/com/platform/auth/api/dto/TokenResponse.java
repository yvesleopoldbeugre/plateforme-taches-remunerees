package com.platform.auth.api.dto;

import com.platform.auth.application.LoginService.LoginResult;

/** Only returned once a session is actually issued — see {@code MfaChallengeResponse} for the
 * password-verified-but-not-yet-MFA-verified intermediate step (Phase 12). */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        boolean mfaSetupRecommended,
        UserSummary user
) {
    public record UserSummary(String id, String email, String status, boolean emailVerified, boolean phoneVerified) {
    }

    public static TokenResponse of(LoginResult result) {
        var user = result.user();
        return new TokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn().toSeconds(),
                result.mfaSetupRecommended(),
                new UserSummary(user.getId().toString(), user.getEmail(), user.getStatus().name(),
                        user.isEmailVerified(), user.isPhoneVerified()));
    }
}
