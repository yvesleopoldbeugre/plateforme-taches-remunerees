package com.platform.withdrawals.application;

import com.platform.common.exception.BusinessException;
import com.platform.common.exception.ConflictException;
import com.platform.common.exception.ForbiddenException;
import com.platform.common.exception.NotFoundException;
import com.platform.payments.application.PaymentMethodService;
import com.platform.payments.application.PaymentProviderRegistry;
import com.platform.payments.application.PaymentProviderResult;
import com.platform.payments.domain.PaymentMethod;
import com.platform.payments.domain.PaymentMethodStatus;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import com.platform.withdrawals.domain.Withdrawal;
import com.platform.withdrawals.domain.WithdrawalStatus;
import com.platform.withdrawals.infrastructure.WithdrawalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for {@link Withdrawal} (UC-17/UC-18, BR-002/BR-040 à BR-043). "Un retrait
 * simulé complète le cycle REQUESTED→COMPLETED" is the explicit Phase 8 roadmap milestone — see
 * {@link #requestWithdrawal} for the small-amount happy path that does exactly that in one call.
 *
 * <p>Hypotheses explicites (non chiffrées dans le dossier de conception, tranchées ici, comme
 * {@code TaskTypes}/{@code RejectionReasons} avant) : montant minimum, taux de frais et seuil de
 * revue manuelle sont des valeurs de configuration ({@code platform.withdrawal.*}), pas des
 * constantes — voir {@code application.yml}. Les frais sont déduits du montant demandé
 * ({@code net_amount = amount - fees}), pas ajoutés par-dessus : c'est la convention fintech la
 * plus courante et elle évite qu'un utilisateur doive calculer une marge pour les frais.
 */
@Service
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final UserService userService;
    private final PaymentMethodService paymentMethodService;
    private final PaymentProviderRegistry providerRegistry;
    private final WalletService walletService;
    private final BigDecimal minimumAmount;
    private final BigDecimal feeRate;
    private final BigDecimal manualReviewThreshold;

    public WithdrawalService(WithdrawalRepository withdrawalRepository, UserService userService,
                              PaymentMethodService paymentMethodService, PaymentProviderRegistry providerRegistry,
                              WalletService walletService,
                              @Value("${platform.withdrawal.minimum-amount:1000}") BigDecimal minimumAmount,
                              @Value("${platform.withdrawal.fee-rate:0.015}") BigDecimal feeRate,
                              @Value("${platform.withdrawal.manual-review-threshold:50000}") BigDecimal manualReviewThreshold) {
        this.withdrawalRepository = withdrawalRepository;
        this.userService = userService;
        this.paymentMethodService = paymentMethodService;
        this.providerRegistry = providerRegistry;
        this.walletService = walletService;
        this.minimumAmount = minimumAmount;
        this.feeRate = feeRate;
        this.manualReviewThreshold = manualReviewThreshold;
    }

    @Transactional
    public Withdrawal requestWithdrawal(UUID userId, UUID paymentMethodId, BigDecimal amount, String idempotencyKey) {
        return withdrawalRepository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
            assertUserCanWithdraw(userId);
            if (amount == null || amount.compareTo(minimumAmount) < 0) {
                throw new BusinessException("WITHDRAWAL_BELOW_MINIMUM", HttpStatus.BAD_REQUEST,
                        "Le montant minimum de retrait est de " + minimumAmount + " (BR-043).");
            }
            PaymentMethod method = paymentMethodService.getOwnedBy(paymentMethodId, WalletOwnerType.USER, userId);
            if (method.getStatus() != PaymentMethodStatus.ACTIVE) {
                throw new ConflictException("PAYMENT_METHOD_INACTIVE", "Ce moyen de paiement n'est plus actif.");
            }

            BigDecimal fees = amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal netAmount = amount.subtract(fees);
            Wallet wallet = walletService.getOrCreateWallet(WalletOwnerType.USER, userId);

            Withdrawal withdrawal = withdrawalRepository.save(
                    new Withdrawal(userId, wallet.getId(), paymentMethodId, amount, fees, netAmount, idempotencyKey));

            // BR-043/DAT §8.3 — reserved immediately, before any review: a second concurrent
            // request against the same balance can't also succeed. Throwing here rolls back the
            // insert above too (same transaction), so no orphan REQUESTED row with nothing reserved.
            walletService.reserve(WalletOwnerType.USER, userId, amount, ReferenceType.WITHDRAWAL, withdrawal.getId(),
                    "withdrawal-reserve:" + withdrawal.getId());

            if (amount.compareTo(manualReviewThreshold) > 0) {
                withdrawal.markUnderReview();
                return withdrawal;
            }
            approveAndProcess(withdrawal, method);
            return withdrawal;
        });
    }

    @Transactional
    public Withdrawal approve(UUID withdrawalId) {
        Withdrawal withdrawal = getById(withdrawalId);
        PaymentMethod method = paymentMethodService.getById(withdrawal.getPaymentMethodId());
        approveAndProcess(withdrawal, method);
        return withdrawal;
    }

    private void approveAndProcess(Withdrawal withdrawal, PaymentMethod method) {
        withdrawal.approve();
        withdrawal.startProcessing();
        PaymentProviderResult result = providerRegistry.get(method.getProvider())
                .initiatePayout(withdrawal.getId(), method.getMaskedIdentifier(), withdrawal.getNetAmount(), "XOF");
        if (result.succeeded()) {
            walletService.consumeReserved(WalletOwnerType.USER, withdrawal.getUserId(), withdrawal.getAmount(),
                    ReferenceType.WITHDRAWAL, withdrawal.getId(), "withdrawal-consume:" + withdrawal.getId());
            withdrawal.complete();
        } else {
            withdrawal.fail(result.failureReason());
            walletService.release(WalletOwnerType.USER, withdrawal.getUserId(), withdrawal.getAmount(),
                    ReferenceType.WITHDRAWAL, withdrawal.getId(), "withdrawal-release-failed:" + withdrawal.getId());
        }
    }

    @Transactional
    public Withdrawal reject(UUID withdrawalId, String reason) {
        Withdrawal withdrawal = getById(withdrawalId);
        withdrawal.reject(reason);
        walletService.release(WalletOwnerType.USER, withdrawal.getUserId(), withdrawal.getAmount(),
                ReferenceType.WITHDRAWAL, withdrawal.getId(), "withdrawal-release-rejected:" + withdrawal.getId());
        return withdrawal;
    }

    @Transactional
    public Withdrawal cancel(UUID withdrawalId, UUID requesterId) {
        Withdrawal withdrawal = getById(withdrawalId);
        withdrawal.assertOwnedBy(requesterId);
        withdrawal.cancel();
        walletService.release(WalletOwnerType.USER, withdrawal.getUserId(), withdrawal.getAmount(),
                ReferenceType.WITHDRAWAL, withdrawal.getId(), "withdrawal-release-cancelled:" + withdrawal.getId());
        return withdrawal;
    }

    public Withdrawal getById(UUID id) {
        return withdrawalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("WITHDRAWAL_NOT_FOUND", "Retrait introuvable."));
    }

    public List<Withdrawal> listByUser(UUID userId) {
        return withdrawalRepository.findByUserIdOrderByRequestedAtDesc(userId);
    }

    public List<Withdrawal> listByStatus(WithdrawalStatus status) {
        return withdrawalRepository.findByStatusOrderByRequestedAtAsc(status);
    }

    /** BR-002 — literally names withdrawal requests among what a suspended account loses. */
    private void assertUserCanWithdraw(UUID userId) {
        User user = userService.getById(userId);
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Ce compte ne peut pas demander de retrait (BR-002).");
        }
    }
}
