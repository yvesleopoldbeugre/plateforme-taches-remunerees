package com.platform.payments.api;

import com.platform.payments.api.dto.InitiateDepositRequest;
import com.platform.payments.api.dto.PaymentResponse;
import com.platform.payments.application.PaymentService;
import com.platform.payments.domain.Payment;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST .../payments} requires the {@code Idempotency-Key} header (DAT §8.3: "Header
 * Idempotency-Key obligatoire sur les opérations financières") — missing it is a 400, not a 500,
 * via the {@code MissingRequestHeaderException} handler added to {@code GlobalExceptionHandler}
 * in this phase.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/v1/organizations/{organizationId}/payments")
    public ResponseEntity<PaymentResponse> deposit(Authentication authentication, @PathVariable UUID organizationId,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      @Valid @RequestBody InitiateDepositRequest request) {
        Payment payment = paymentService.initiateDeposit(organizationId, currentUserId(authentication),
                request.amount(), request.paymentMethodId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.of(payment));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/payments")
    public List<PaymentResponse> list(Authentication authentication, @PathVariable UUID organizationId) {
        return paymentService.listByOrganization(organizationId, currentUserId(authentication))
                .stream().map(PaymentResponse::of).toList();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
