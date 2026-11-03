package com.platform.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "Format E.164 attendu (ex. +2250700000000)") String phone,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(min = 2, max = 255) String fullName,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays ISO 3166-1 alpha-2 (ex. CI)") String country
) {
}
