package com.platform.participations.domain.event;

import com.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@code ParticipationService#submit} when the submission it just recorded has the
 * same content hash as an earlier one on the same task (BR-051). The {@code fraud} module
 * listens for this to raise a {@code FraudEvent} — {@code participations} never touches that
 * table directly (DAT §8.2). Carries {@code userId}/{@code campaignId} so {@code fraud} (a
 * transverse module) never has to call back into {@code participations}/{@code campaigns} to
 * resolve them, the same reasoning as {@code SubmissionApprovedEvent} in Phase 7.
 */
public record DuplicateSubmissionDetectedEvent(
        UUID submissionId,
        UUID duplicateOfSubmissionId,
        UUID participationId,
        UUID taskId,
        UUID campaignId,
        UUID userId,
        Instant occurredAt
) implements DomainEvent {

    public DuplicateSubmissionDetectedEvent(UUID submissionId, UUID duplicateOfSubmissionId, UUID participationId,
                                             UUID taskId, UUID campaignId, UUID userId) {
        this(submissionId, duplicateOfSubmissionId, participationId, taskId, campaignId, userId, Instant.now());
    }
}
