package com.platform.payments.application;

import com.platform.common.exception.BusinessException;
import com.platform.common.exception.NotFoundException;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.OrganizationRole;
import com.platform.payments.domain.PaymentMethod;
import com.platform.payments.domain.PaymentMethodTypes;
import com.platform.payments.infrastructure.PaymentMethodRepository;
import com.platform.wallet.domain.WalletOwnerType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for {@link PaymentMethod} — shared by {@code payments} (deposits) and, via its own module, {@code withdrawals} (payouts). */
@Service
public class PaymentMethodService {

    private final PaymentMethodRepository repository;
    private final PaymentProviderRegistry providerRegistry;
    private final OrganizationService organizationService;

    public PaymentMethodService(PaymentMethodRepository repository, PaymentProviderRegistry providerRegistry,
                                 OrganizationService organizationService) {
        this.repository = repository;
        this.providerRegistry = providerRegistry;
        this.organizationService = organizationService;
    }

    @Transactional
    public PaymentMethod registerForUser(UUID userId, String provider, String type, String maskedIdentifier) {
        return register(WalletOwnerType.USER, userId, provider, type, maskedIdentifier);
    }

    @Transactional
    public PaymentMethod registerForOrganization(UUID organizationId, UUID requesterId, String provider, String type,
                                                   String maskedIdentifier) {
        organizationService.assertActiveMembership(organizationId, requesterId, OrganizationRole.OWNER, OrganizationRole.MANAGER);
        return register(WalletOwnerType.ORGANIZATION, organizationId, provider, type, maskedIdentifier);
    }

    private PaymentMethod register(WalletOwnerType ownerType, UUID ownerId, String provider, String type, String maskedIdentifier) {
        providerRegistry.get(provider); // throws UNSUPPORTED_PAYMENT_PROVIDER if unknown
        if (!PaymentMethodTypes.SUPPORTED.contains(type)) {
            throw new BusinessException("UNSUPPORTED_PAYMENT_METHOD_TYPE", HttpStatus.BAD_REQUEST,
                    "Type de moyen de paiement non supporté. Valeurs possibles : " + PaymentMethodTypes.SUPPORTED);
        }
        boolean isFirst = repository.findByOwnerTypeAndOwnerId(ownerType, ownerId).isEmpty();
        return repository.save(new PaymentMethod(ownerType, ownerId, provider, type, maskedIdentifier, isFirst));
    }

    public List<PaymentMethod> listForUser(UUID userId) {
        return repository.findByOwnerTypeAndOwnerId(WalletOwnerType.USER, userId);
    }

    public List<PaymentMethod> listForOrganization(UUID organizationId, UUID requesterId) {
        organizationService.assertActiveMembership(organizationId, requesterId);
        return repository.findByOwnerTypeAndOwnerId(WalletOwnerType.ORGANIZATION, organizationId);
    }

    public PaymentMethod getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PAYMENT_METHOD_NOT_FOUND", "Moyen de paiement introuvable."));
    }

    /** Never reveals whether the id exists for a different owner — a mismatch is the same 404. */
    public PaymentMethod getOwnedBy(UUID id, WalletOwnerType ownerType, UUID ownerId) {
        PaymentMethod method = getById(id);
        if (method.getOwnerType() != ownerType || !method.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("PAYMENT_METHOD_NOT_FOUND", "Moyen de paiement introuvable.");
        }
        return method;
    }
}
