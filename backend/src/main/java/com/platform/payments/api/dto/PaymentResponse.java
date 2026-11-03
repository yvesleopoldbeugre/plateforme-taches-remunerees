package com.platform.payments.api.dto;

import com.platform.payments.domain.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id, UUID organizationId, UUID paymentMethodId, String provider, String providerReference,
        BigDecimal amount, String status, Instant createdAt, Instant completedAt
) {
    public static PaymentResponse of(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrganizationId(), p.getPaymentMethodId(), p.getProvider(),
                p.getProviderReference(), p.getAmount(), p.getStatus().name(), p.getCreatedAt(), p.getCompletedAt());
    }
}
