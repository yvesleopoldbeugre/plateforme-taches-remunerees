package com.platform.organizations.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @Size(max = 255) String legalName,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays ISO 3166-1 alpha-2 (ex. CI)") String country
) {
}
