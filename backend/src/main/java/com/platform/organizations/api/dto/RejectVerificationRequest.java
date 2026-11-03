package com.platform.organizations.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectVerificationRequest(@NotBlank String reason) {
}
