package com.platform.withdrawals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.common.exception.BusinessException;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.Organization;
import com.platform.payments.application.PaymentMethodService;
import com.platform.payments.application.PaymentProviderRegistry;
import com.platform.payments.application.PaymentService;
import com.platform.payments.application.SimulatedPaymentProvider;
import com.platform.payments.domain.Payment;
import com.platform.payments.domain.PaymentMethod;
import com.platform.payments.domain.PaymentMethodTypes;
import com.platform.payments.domain.PaymentStatus;
import com.platform.users.domain.User;
import com.platform.users.infrastructure.UserRepository;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import com.platform.withdrawals.application.WithdrawalService;
import com.platform.withdrawals.domain.Withdrawal;
import com.platform.withdrawals.domain.WithdrawalStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The explicit Phase 8 roadmap milestone (DAT §11): "Un retrait simulé complète le cycle
 * REQUESTED→COMPLETED." {@link #smallWithdrawalCompletesRequestedToCompletedCycleInOneCall}
 * is exactly that; the rest of this class exercises the surrounding state machine (manual
 * review, rejection, cancellation, minimum amount, insufficient balance, idempotency) and ties
 * back to Phase 7's ledger invariant to confirm withdrawals don't break it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WithdrawalLifecycleTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private PaymentMethodService paymentMethodService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentProviderRegistry providerRegistry;
    @Autowired
    private WalletService walletService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationService organizationService;

    private static int counter = 0;

    private User persistVerifiedUser() {
        int n = counter++;
        User user = new User("withdrawal-user-" + n + "-" + UUID.randomUUID() + "@example.com",
                "+225070000" + String.format("%04d", n), "irrelevant-hash");
        user.markEmailVerified();
        user.markPhoneVerified();
        return userRepository.saveAndFlush(user);
    }

    private PaymentMethod mobileMoneyMethodFor(UUID userId) {
        return paymentMethodService.registerForUser(userId, SimulatedPaymentProvider.CODE, PaymentMethodTypes.MOBILE_MONEY, "+225****0000");
    }

    private Wallet fundUserWallet(UUID userId, BigDecimal amount) {
        return walletService.credit(WalletOwnerType.USER, userId, amount, ReferenceType.REWARD, UUID.randomUUID(),
                "test-fund:" + UUID.randomUUID());
    }

    @Test
    void smallWithdrawalCompletesRequestedToCompletedCycleInOneCall() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(10_000));

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                user.getId(), method.getId(), BigDecimal.valueOf(5_000), "test-withdraw:" + UUID.randomUUID());

        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.COMPLETED);
        assertThat(withdrawal.getFees()).isEqualByComparingTo("75.00"); // 1.5% of 5000
        assertThat(withdrawal.getNetAmount()).isEqualByComparingTo("4925.00");

        Wallet wallet = walletService.getWallet(withdrawal.getWalletId());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("5000.00");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getLifetimeWithdrawn()).isEqualByComparingTo("5000.00");

        WalletService.ReconstructedBalances reconstructed = walletService.reconstructBalances(wallet.getId());
        assertThat(reconstructed.availableBalance()).isEqualByComparingTo(wallet.getAvailableBalance());
        assertThat(reconstructed.reservedBalance()).isEqualByComparingTo(wallet.getReservedBalance());
    }

    @Test
    void largeWithdrawalRequiresManualReviewThenCompletesOnApproval() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(200_000));

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                user.getId(), method.getId(), BigDecimal.valueOf(100_000), "test-withdraw:" + UUID.randomUUID());
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.UNDER_REVIEW);

        Wallet reserved = walletService.getWallet(withdrawal.getWalletId());
        assertThat(reserved.getReservedBalance()).isEqualByComparingTo("100000.00");

        Withdrawal approved = withdrawalService.approve(withdrawal.getId());
        assertThat(approved.getStatus()).isEqualTo(WithdrawalStatus.COMPLETED);

        Wallet wallet = walletService.getWallet(withdrawal.getWalletId());
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getLifetimeWithdrawn()).isEqualByComparingTo("100000.00");
    }

    @Test
    void rejectionReleasesReservedFundsBackToAvailable() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(200_000));

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                user.getId(), method.getId(), BigDecimal.valueOf(60_000), "test-withdraw:" + UUID.randomUUID());
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.UNDER_REVIEW);

        Withdrawal rejected = withdrawalService.reject(withdrawal.getId(), "Document d'identité illisible");
        assertThat(rejected.getStatus()).isEqualTo(WithdrawalStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Document d'identité illisible");

        Wallet wallet = walletService.getWallet(withdrawal.getWalletId());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("200000.00");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getLifetimeWithdrawn()).isEqualByComparingTo("0");
    }

    @Test
    void userCanCancelWhileUnderReviewAndFundsAreReleased() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(200_000));

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                user.getId(), method.getId(), BigDecimal.valueOf(60_000), "test-withdraw:" + UUID.randomUUID());

        Withdrawal cancelled = withdrawalService.cancel(withdrawal.getId(), user.getId());
        assertThat(cancelled.getStatus()).isEqualTo(WithdrawalStatus.CANCELLED);

        Wallet wallet = walletService.getWallet(withdrawal.getWalletId());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("200000.00");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void anotherUserCannotCancelSomeoneElsesWithdrawal() {
        User owner = persistVerifiedUser();
        User stranger = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(owner.getId());
        fundUserWallet(owner.getId(), BigDecimal.valueOf(10_000));

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                owner.getId(), method.getId(), BigDecimal.valueOf(2_000), "test-withdraw:" + UUID.randomUUID());

        assertThatThrownBy(() -> withdrawalService.cancel(withdrawal.getId(), stranger.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("NOT_WITHDRAWAL_OWNER"));
    }

    @Test
    void belowMinimumAmountIsRejectedWithoutTouchingTheWallet() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(10_000));

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(
                user.getId(), method.getId(), BigDecimal.valueOf(100), "test-withdraw:" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("WITHDRAWAL_BELOW_MINIMUM"));

        Wallet wallet = walletService.getOrCreateWallet(WalletOwnerType.USER, user.getId());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void insufficientBalanceIsRejectedAndRollsBackTheWithdrawalRow() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(1_000));

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(
                user.getId(), method.getId(), BigDecimal.valueOf(5_000), "test-withdraw:" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INSUFFICIENT_BALANCE"));

        assertThat(withdrawalService.listByUser(user.getId())).isEmpty();
    }

    @Test
    void retriedIdempotencyKeyDoesNotCreateASecondWithdrawal() {
        User user = persistVerifiedUser();
        PaymentMethod method = mobileMoneyMethodFor(user.getId());
        fundUserWallet(user.getId(), BigDecimal.valueOf(10_000));
        String key = "test-withdraw:" + UUID.randomUUID();

        Withdrawal first = withdrawalService.requestWithdrawal(user.getId(), method.getId(), BigDecimal.valueOf(2_000), key);
        Withdrawal replay = withdrawalService.requestWithdrawal(user.getId(), method.getId(), BigDecimal.valueOf(2_000), key);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(withdrawalService.listByUser(user.getId())).hasSize(1);

        Wallet wallet = walletService.getWallet(first.getWalletId());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("8000.00"); // reserved+consumed once, not twice
    }

    @Test
    void simulatedDepositCreditsOrganizationWalletThroughThePaymentProvider() {
        User owner = persistVerifiedUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Phase8", "Org Phase8 SARL", "CI");

        Payment payment = paymentService.initiateDeposit(org.getId(), owner.getId(), BigDecimal.valueOf(20_000), null,
                "test-deposit:" + UUID.randomUUID());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getProvider()).isEqualTo(SimulatedPaymentProvider.CODE);
        assertThat(payment.getProviderReference()).isNotBlank();

        Wallet wallet = walletService.getOrCreateWallet(WalletOwnerType.ORGANIZATION, org.getId());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("20000.00");

        // Idempotent replay: same key, no double-credit.
        Payment replay = paymentService.initiateDeposit(org.getId(), owner.getId(), BigDecimal.valueOf(20_000), null,
                payment.getIdempotencyKey());
        assertThat(replay.getId()).isEqualTo(payment.getId());
        Wallet walletAfterReplay = walletService.getOrCreateWallet(WalletOwnerType.ORGANIZATION, org.getId());
        assertThat(walletAfterReplay.getAvailableBalance()).isEqualByComparingTo("20000.00");
    }

    @Test
    void registeringAPaymentMethodWithAnUnsupportedProviderFails() {
        User user = persistVerifiedUser();
        assertThatThrownBy(() -> paymentMethodService.registerForUser(user.getId(), "ORANGE_MONEY", PaymentMethodTypes.MOBILE_MONEY, "+225****0000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("UNSUPPORTED_PAYMENT_PROVIDER"));
    }

    @Test
    void firstRegisteredPaymentMethodBecomesDefault() {
        User user = persistVerifiedUser();
        PaymentMethod first = mobileMoneyMethodFor(user.getId());
        PaymentMethod second = paymentMethodService.registerForUser(
                user.getId(), SimulatedPaymentProvider.CODE, PaymentMethodTypes.MOBILE_MONEY, "+225****1111");

        assertThat(first.isDefault()).isTrue();
        assertThat(second.isDefault()).isFalse();
        List<PaymentMethod> methods = paymentMethodService.listForUser(user.getId());
        assertThat(methods).hasSize(2);
    }
}
