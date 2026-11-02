package com.platform.common.event;

import java.time.Instant;

/**
 * Marker for domain events published on the internal Spring {@code ApplicationEventPublisher}.
 * Business modules (Campaigns, Participations, Wallet, ...) publish events; the transverse
 * modules (Notifications, Fraud, Audit) subscribe as listeners — see DAT §2 and §8.2.
 */
public interface DomainEvent {

    Instant occurredAt();
}
