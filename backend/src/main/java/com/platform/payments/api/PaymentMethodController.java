package com.platform.payments.api;

import com.platform.payments.api.dto.PaymentMethodResponse;
import com.platform.payments.api.dto.RegisterPaymentMethodRequest;
import com.platform.payments.application.PaymentMethodService;
import com.platform.payments.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Moyens de paiement", description = "Enregistrement des moyens de paiement (Mobile Money, "
        + "compte bancaire) d'un utilisateur ou d'une organisation — utilisés pour les dépôts et les retraits.")
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @Operation(summary = "Enregistrer mon moyen de paiement", description = "Le premier enregistré devient "
            + "automatiquement le moyen par défaut.")
    @PostMapping("/api/v1/payment-methods/me")
    public ResponseEntity<PaymentMethodResponse> registerMine(Authentication authentication,
                                                                 @Valid @RequestBody RegisterPaymentMethodRequest request) {
        PaymentMethod method = paymentMethodService.registerForUser(
                currentUserId(authentication), request.provider(), request.type(), request.maskedIdentifier());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentMethodResponse.of(method));
    }

    @Operation(summary = "Lister mes moyens de paiement")
    @GetMapping("/api/v1/payment-methods/me")
    public List<PaymentMethodResponse> myMethods(Authentication authentication) {
        return paymentMethodService.listForUser(currentUserId(authentication)).stream().map(PaymentMethodResponse::of).toList();
    }

    @Operation(summary = "Enregistrer un moyen de paiement pour une organisation", description = "Réservé à OWNER/MANAGER.")
    @PostMapping("/api/v1/organizations/{organizationId}/payment-methods")
    public ResponseEntity<PaymentMethodResponse> registerForOrganization(Authentication authentication,
                                                                            @PathVariable UUID organizationId,
                                                                            @Valid @RequestBody RegisterPaymentMethodRequest request) {
        PaymentMethod method = paymentMethodService.registerForOrganization(
                organizationId, currentUserId(authentication), request.provider(), request.type(), request.maskedIdentifier());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentMethodResponse.of(method));
    }

    @Operation(summary = "Lister les moyens de paiement d'une organisation")
    @GetMapping("/api/v1/organizations/{organizationId}/payment-methods")
    public List<PaymentMethodResponse> organizationMethods(Authentication authentication, @PathVariable UUID organizationId) {
        return paymentMethodService.listForOrganization(organizationId, currentUserId(authentication))
                .stream().map(PaymentMethodResponse::of).toList();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
