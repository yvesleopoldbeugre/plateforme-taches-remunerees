package com.platform.organizations.domain.event;

import com.platform.common.event.DomainEvent;
import com.platform.organizations.domain.KycStatus;
import com.platform.organizations.domain.OrganizationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on every admin-driven change to an organization's {@code status} and/or
 * {@code kyc_status} — verification, rejection, suspension, reactivation (UC-21 and the
 * verification flow deferred since Phase 3). Carries both fields even when only one changed, so
 * the {@code audit} module always has a complete before/after snapshot in one event.
 */
public record OrganizationStatusChangedEvent(
        UUID actorId,
        UUID organizationId,
        OrganizationStatus previousStatus,
        OrganizationStatus newStatus,
        KycStatus previousKycStatus,
        KycStatus newKycStatus,
        String reason,
        Instant occurredAt
) implements DomainEvent {

    public OrganizationStatusChangedEvent(UUID actorId, UUID organizationId, OrganizationStatus previousStatus,
                                           OrganizationStatus newStatus, KycStatus previousKycStatus,
                                           KycStatus newKycStatus, String reason) {
        this(actorId, organizationId, previousStatus, newStatus, previousKycStatus, newKycStatus, reason, Instant.now());
    }
}
