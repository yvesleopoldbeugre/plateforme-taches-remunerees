package com.platform.payments.api.dto;

import com.platform.payments.domain.PaymentMethod;
import java.time.Instant;
import java.util.UUID;

public record PaymentMethodResponse(
        UUID id, String ownerType, UUID ownerId, String provider, String type, String maskedIdentifier,
        boolean isDefault, String status, Instant createdAt
) {
    public static PaymentMethodResponse of(PaymentMethod m) {
        return new PaymentMethodResponse(m.getId(), m.getOwnerType().name(), m.getOwnerId(), m.getProvider(),
                m.getType(), m.getMaskedIdentifier(), m.isDefault(), m.getStatus().name(), m.getCreatedAt());
    }
}
