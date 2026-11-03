package com.platform.tasks.api.dto;

import com.platform.tasks.domain.Task;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id, UUID campaignId, String type, String title, String instructions, BigDecimal rewardAmount,
        int maxParticipants, int currentParticipants, String expectedProofType, String status,
        int reservationTtlMinutes, Instant createdAt
) {
    public static TaskResponse of(Task t) {
        return new TaskResponse(t.getId(), t.getCampaignId(), t.getType(), t.getTitle(), t.getInstructions(),
                t.getRewardAmount(), t.getMaxParticipants(), t.getCurrentParticipants(), t.getExpectedProofType(),
                t.getStatus().name(), t.getReservationTtlMinutes(), t.getCreatedAt());
    }
}
