package com.platform.auth.api.dto;

import com.platform.auth.application.LoginService.LoginResult;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
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
                new UserSummary(user.getId().toString(), user.getEmail(), user.getStatus().name(),
                        user.isEmailVerified(), user.isPhoneVerified()));
    }
}
