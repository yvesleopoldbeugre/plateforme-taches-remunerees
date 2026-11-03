package com.platform.fraud.api.dto;

import com.platform.fraud.domain.FraudEvent;
import java.time.Instant;
import java.util.UUID;

public record FraudEventResponse(
        UUID id, String type, String riskLevel, UUID userId, UUID campaignId, Instant detectedAt, String reason,
        String status, String adminDecision, UUID reviewedBy, Instant reviewedAt
) {
    public static FraudEventResponse of(FraudEvent e) {
        return new FraudEventResponse(e.getId(), e.getType().name(), e.getRiskLevel().name(), e.getUserId(),
                e.getCampaignId(), e.getDetectedAt(), e.getReason(), e.getStatus().name(), e.getAdminDecision(),
                e.getReviewedBy(), e.getReviewedAt());
    }
}
