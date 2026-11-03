package com.platform.campaigns.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotBlank @Size(max = 64) String category,
        @NotNull Instant startDate,
        @NotNull Instant endDate
) {
}
