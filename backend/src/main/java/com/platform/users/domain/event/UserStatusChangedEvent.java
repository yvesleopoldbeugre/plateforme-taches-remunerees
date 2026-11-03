package com.platform.users.domain.event;

import com.platform.common.event.DomainEvent;
import com.platform.users.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every admin-driven status transition (suspend/reactivate/ban — UC-21). The
 * {@code audit} module listens for this to write an immutable {@code AuditLog} row; {@code users}
 * itself never touches audit (DAT §8.2 — transverse modules subscribe, business modules publish).
 */
public record UserStatusChangedEvent(
        UUID actorId,
        UUID userId,
        UserStatus previousStatus,
        UserStatus newStatus,
        Instant occurredAt
) implements DomainEvent {

    public UserStatusChangedEvent(UUID actorId, UUID userId, UserStatus previousStatus, UserStatus newStatus) {
        this(actorId, userId, previousStatus, newStatus, Instant.now());
    }
}
