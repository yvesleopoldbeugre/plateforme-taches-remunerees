package com.platform.campaigns.api.dto;

import com.platform.campaigns.domain.Campaign;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
        UUID id, UUID organizationId, String title, String description, String category, String status,
        BigDecimal budgetTotal, BigDecimal budgetReserved, BigDecimal budgetConsumed, String currency,
        Instant startDate, Instant endDate, Instant createdAt
) {
    public static CampaignResponse of(Campaign c) {
        return new CampaignResponse(c.getId(), c.getOrganizationId(), c.getTitle(), c.getDescription(),
                c.getCategory(), c.getStatus().name(), c.getBudgetTotal(), c.getBudgetReserved(),
                c.getBudgetConsumed(), c.getCurrency(), c.getStartDate(), c.getEndDate(), c.getCreatedAt());
    }
}
