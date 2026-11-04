package com.platform.users.domain.event;

import com.platform.common.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every self-service security action (MFA enabled/disabled, password reset
 * completed) — CDCT §8: "toute action sensible journalisée en AuditLog, immuable." Deliberately
 * generic ({@code action} is a short code, not its own event type per action) to avoid an event
 * class per security action; {@code audit} just writes it down verbatim.
 */
public record UserSecurityActionEvent(
        UUID actorId,
        UUID userId,
        String action,
        Instant occurredAt
) implements DomainEvent {

    public UserSecurityActionEvent(UUID actorId, UUID userId, String action) {
        this(actorId, userId, action, Instant.now());
    }
}
