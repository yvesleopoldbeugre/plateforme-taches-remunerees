package com.platform.campaigns.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectCampaignRequest(@NotBlank String reason) {
}
