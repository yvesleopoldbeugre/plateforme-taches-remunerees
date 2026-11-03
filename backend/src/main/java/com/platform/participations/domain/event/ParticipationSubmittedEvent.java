package com.platform.participations.domain.event;

import com.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published once a submission is successfully recorded for a participation — the {@code
 * validation} module listens for this to attempt auto-validation (UC-11, US-052). Carries the
 * content so the listener does not need to re-query it.
 */
public record ParticipationSubmittedEvent(
        UUID participationId,
        UUID taskId,
        UUID submissionId,
        Map<String, Object> content,
        Instant occurredAt
) implements DomainEvent {

    public ParticipationSubmittedEvent(UUID participationId, UUID taskId, UUID submissionId, Map<String, Object> content) {
        this(participationId, taskId, submissionId, content, Instant.now());
    }
}
