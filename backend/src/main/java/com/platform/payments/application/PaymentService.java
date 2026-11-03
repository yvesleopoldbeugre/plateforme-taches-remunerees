package com.platform.payments.application;

import com.platform.common.exception.BusinessException;
import com.platform.common.exception.NotFoundException;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationRole;
import com.platform.payments.domain.Payment;
import com.platform.payments.domain.PaymentMethod;
import com.platform.payments.infrastructure.PaymentRepository;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.WalletOwnerType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for {@link Payment} — an organization's deposit into its own wallet.
 * Replaces the ad-hoc {@code WalletController#deposit} stand-in from Phase 7: this is now the
 * one path that credits an organization's wallet from the outside, and it goes through a real
 * {@link PaymentProvider} rather than crediting directly.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMethodService paymentMethodService;
    private final PaymentProviderRegistry providerRegistry;
    private final WalletService walletService;
    private final OrganizationService organizationService;

    public PaymentService(PaymentRepository paymentRepository, PaymentMethodService paymentMethodService,
                           PaymentProviderRegistry providerRegistry, WalletService walletService,
                           OrganizationService organizationService) {
        this.paymentRepository = paymentRepository;
        this.paymentMethodService = paymentMethodService;
        this.providerRegistry = providerRegistry;
        this.walletService = walletService;
        this.organizationService = organizationService;
    }

    @Transactional
    public Payment initiateDeposit(UUID organizationId, UUID requesterId, BigDecimal amount, UUID paymentMethodId,
                                    String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            organizationService.assertActiveMembership(organizationId, requesterId, OrganizationRole.OWNER, OrganizationRole.MANAGER);
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_AMOUNT", HttpStatus.BAD_REQUEST, "Le montant doit être strictement positif.");
            }

            String providerCode = SimulatedPaymentProvider.CODE;
            if (paymentMethodId != null) {
                PaymentMethod method = paymentMethodService.getOwnedBy(paymentMethodId, WalletOwnerType.ORGANIZATION, organizationId);
                providerCode = method.getProvider();
            }

            Payment payment = paymentRepository.save(new Payment(organizationId, paymentMethodId, providerCode, amount, idempotencyKey));
            PaymentProviderResult result = providerRegistry.get(providerCode).initiateDeposit(payment.getId(), amount, "XOF");
            if (result.succeeded()) {
                payment.complete(result.providerReference());
                walletService.credit(WalletOwnerType.ORGANIZATION, organizationId, amount,
                        ReferenceType.PAYMENT, payment.getId(), "payment-credit:" + payment.getId());
            } else {
                payment.fail();
            }
            return payment;
        });
    }

    public Payment getById(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "Paiement introuvable."));
    }

    public List<Payment> listByOrganization(UUID organizationId, UUID requesterId) {
        organizationService.assertActiveMembership(organizationId, requesterId);
        return paymentRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }
}
