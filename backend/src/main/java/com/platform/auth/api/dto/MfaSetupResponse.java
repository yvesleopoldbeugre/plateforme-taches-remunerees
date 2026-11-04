package com.platform.auth.api.dto;

import com.platform.auth.application.MfaService.MfaSetup;

/** {@code provisioningUri} is an {@code otpauth://} URI — the client renders it as a QR code
 * (or lets the user type {@code secret} manually); rendering itself is a frontend concern. */
public record MfaSetupResponse(String secret, String provisioningUri) {
    public static MfaSetupResponse of(MfaSetup setup) {
        return new MfaSetupResponse(setup.secret(), setup.provisioningUri());
    }
}
