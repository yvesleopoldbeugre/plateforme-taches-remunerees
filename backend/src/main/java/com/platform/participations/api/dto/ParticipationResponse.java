package com.platform.participations.api.dto;

import com.platform.participations.domain.TaskParticipation;
import java.time.Instant;
import java.util.UUID;

public record ParticipationResponse(
        UUID id, UUID taskId, UUID userId, String status,
        Instant reservedAt, Instant expiresAt, Instant submittedAt, Instant resolvedAt
) {
    public static ParticipationResponse of(TaskParticipation p) {
        return new ParticipationResponse(p.getId(), p.getTaskId(), p.getUserId(), p.getStatus().name(),
                p.getReservedAt(), p.getExpiresAt(), p.getSubmittedAt(), p.getResolvedAt());
    }
}
