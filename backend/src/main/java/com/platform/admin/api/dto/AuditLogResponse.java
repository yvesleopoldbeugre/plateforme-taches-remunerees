package com.platform.admin.api.dto;

import com.platform.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id, String actorType, UUID actorId, String action, String resourceType, UUID resourceId,
        String beforeState, String afterState, Instant createdAt
) {
    public static AuditLogResponse of(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorType().name(), log.getActorId(), log.getAction(),
                log.getResourceType(), log.getResourceId(), log.getBeforeState(), log.getAfterState(), log.getCreatedAt());
    }
}
