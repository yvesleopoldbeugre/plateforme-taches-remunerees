package com.platform.payments.infrastructure;

import com.platform.payments.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
