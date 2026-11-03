package com.platform.validation.domain.event;

import com.platform.common.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every approval (manual or auto). The {@code wallet} module listens for this to
 * create the {@code Reward} and credit the user's wallet, and to release the matching amount
 * from the organization's reserved budget — validation itself never touches money (DAT §8.2
 * module boundaries). {@code campaignId}/{@code organizationId} are carried on the event
 * precisely so {@code wallet} (a dependency-free noyau) never has to call back into
 * {@code campaigns}/{@code tasks} to resolve them.
 */
public record SubmissionApprovedEvent(
        UUID participationId,
        UUID taskId,
        UUID campaignId,
        UUID organizationId,
        UUID userId,
        BigDecimal rewardAmount,
        String currency,
        Instant occurredAt
) implements DomainEvent {

    public SubmissionApprovedEvent(UUID participationId, UUID taskId, UUID campaignId, UUID organizationId,
                                    UUID userId, BigDecimal rewardAmount, String currency) {
        this(participationId, taskId, campaignId, organizationId, userId, rewardAmount, currency, Instant.now());
    }
}
