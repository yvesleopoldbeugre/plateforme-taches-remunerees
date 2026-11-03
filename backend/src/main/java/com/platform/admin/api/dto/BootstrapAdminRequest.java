package com.platform.admin.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BootstrapAdminRequest(
        @NotBlank String secret,
        @NotBlank String email
) {
}
