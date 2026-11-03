package com.platform.validation.domain.event;

import com.platform.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every approval (manual or auto). Phase 7's {@code wallet} module will listen for
 * this to create the {@code Reward} and credit the user's wallet — validation itself never
 * touches money (DAT §8.2 module boundaries).
 */
public record SubmissionApprovedEvent(
        UUID participationId,
        UUID taskId,
        UUID userId,
        BigDecimal rewardAmount,
        String currency,
        Instant occurredAt
) implements DomainEvent {

    public SubmissionApprovedEvent(UUID participationId, UUID taskId, UUID userId, BigDecimal rewardAmount, String currency) {
        this(participationId, taskId, userId, rewardAmount, currency, Instant.now());
    }
}
