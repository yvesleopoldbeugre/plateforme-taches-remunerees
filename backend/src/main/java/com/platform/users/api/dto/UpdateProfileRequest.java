package com.platform.users.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(min = 2, max = 255) String fullName,
        @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays ISO 3166-1 alpha-2 (ex. CI)") String country,
        @Size(max = 128) String city,
        LocalDate birthDate,
        @Pattern(regexp = "^[a-z]{2}$", message = "Code langue ISO 639-1 (ex. fr)") String language
) {
}
