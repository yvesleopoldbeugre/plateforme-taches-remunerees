package com.platform.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Shared by {@code /mfa/enable} and {@code /mfa/disable}. */
public record MfaCodeRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
) {
}
