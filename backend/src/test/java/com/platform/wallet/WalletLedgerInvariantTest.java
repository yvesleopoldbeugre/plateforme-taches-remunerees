package com.platform.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.common.exception.BusinessException;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.Wallet;
import com.platform.wallet.domain.WalletOwnerType;
import com.platform.wallet.infrastructure.WalletTransactionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The explicit Phase 7 milestone from the DAT roadmap (§11): "Invariant solde = somme ledger
 * vérifié par test." {@link WalletService#reconstructBalances} replays a wallet's
 * {@code wallet_transactions} from scratch; every test here asserts that replay agrees with the
 * live {@link Wallet} row after a sequence of real operations — not just that each operation
 * "looks right" in isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WalletLedgerInvariantTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void hikariPoolSize(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
    }

    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletTransactionRepository transactionRepository;

    private void assertLedgerMatchesLiveBalance(Wallet wallet) {
        WalletService.ReconstructedBalances reconstructed = walletService.reconstructBalances(wallet.getId());
        Wallet live = walletService.getWallet(wallet.getId());
        assertThat(reconstructed.availableBalance()).as("available_balance reconstructed from ledger")
                .isEqualByComparingTo(live.getAvailableBalance());
        assertThat(reconstructed.reservedBalance()).as("reserved_balance reconstructed from ledger")
                .isEqualByComparingTo(live.getReservedBalance());
    }

    @Test
    void ledgerReconstructsBalanceThroughDepositReserveReleaseAndConsume() {
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        Wallet wallet = walletService.credit(WalletOwnerType.ORGANIZATION, organizationId, BigDecimal.valueOf(100_000),
                ReferenceType.PAYMENT, null, "test-deposit:" + UUID.randomUUID());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("100000");
        assertLedgerMatchesLiveBalance(wallet);

        wallet = walletService.reserve(WalletOwnerType.ORGANIZATION, organizationId, BigDecimal.valueOf(60_000),
                ReferenceType.CAMPAIGN_BUDGET, campaignId, "test-reserve:" + campaignId);
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("40000");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("60000");
        assertLedgerMatchesLiveBalance(wallet);

        // Consume part of what was reserved (a reward actually paid out of this campaign's budget).
        wallet = walletService.consumeReserved(WalletOwnerType.ORGANIZATION, organizationId, BigDecimal.valueOf(15_000),
                ReferenceType.CAMPAIGN_BUDGET, campaignId, "test-consume:" + UUID.randomUUID());
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("40000");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("45000");
        assertLedgerMatchesLiveBalance(wallet);

        // Cancel the campaign: release what's left reserved (60000 - 15000 consumed = 45000) back to available.
        wallet = walletService.release(WalletOwnerType.ORGANIZATION, organizationId, BigDecimal.valueOf(45_000),
                ReferenceType.CAMPAIGN_BUDGET, campaignId, "test-release:" + campaignId);
        assertThat(wallet.getAvailableBalance()).isEqualByComparingTo("85000");
        assertThat(wallet.getReservedBalance()).isEqualByComparingTo("0");
        assertLedgerMatchesLiveBalance(wallet);

        assertThat(transactionRepository.findByWalletIdOrderByCreatedAtAsc(wallet.getId())).hasSize(4);
    }

    @Test
    void reserveBeyondAvailableBalanceFailsAndLeavesLedgerConsistent() {
        UUID organizationId = UUID.randomUUID();
        Wallet wallet = walletService.credit(WalletOwnerType.ORGANIZATION, organizationId, BigDecimal.valueOf(1_000),
                ReferenceType.PAYMENT, null, "test-deposit:" + UUID.randomUUID());

        assertThatThrownBy(() -> walletService.reserve(WalletOwnerType.ORGANIZATION, organizationId, BigDecimal.valueOf(5_000),
                ReferenceType.CAMPAIGN_BUDGET, UUID.randomUUID(), "test-overreserve:" + UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INSUFFICIENT_BALANCE"));

        Wallet reloaded = walletService.getWallet(wallet.getId());
        assertThat(reloaded.getAvailableBalance()).isEqualByComparingTo("1000");
        assertThat(reloaded.getReservedBalance()).isEqualByComparingTo("0");
        assertLedgerMatchesLiveBalance(reloaded);
    }

    @Test
    void creditIsIdempotentOnRetriedKey() {
        UUID userId = UUID.randomUUID();
        String key = "test-reward-credit:" + UUID.randomUUID();

        Wallet first = walletService.credit(WalletOwnerType.USER, userId, BigDecimal.valueOf(2_500),
                ReferenceType.REWARD, UUID.randomUUID(), key);
        Wallet replay = walletService.credit(WalletOwnerType.USER, userId, BigDecimal.valueOf(2_500),
                ReferenceType.REWARD, UUID.randomUUID(), key);

        assertThat(replay.getAvailableBalance()).isEqualByComparingTo(first.getAvailableBalance());
        assertThat(replay.getAvailableBalance()).isEqualByComparingTo("2500");
        assertThat(transactionRepository.findByWalletIdOrderByCreatedAtAsc(first.getId())).hasSize(1);
        assertLedgerMatchesLiveBalance(replay);
    }

    /**
     * Mirrors {@code ParticipationConcurrencyTest}'s BR-022 proof, applied to BR-010: a single
     * conditional UPDATE (not read-then-write) must keep {@code available_balance} from ever
     * going negative under real concurrent reservations.
     */
    @Test
    void concurrentReservationsNeverOverdrawAvailableBalance() throws Exception {
        UUID organizationId = UUID.randomUUID();
        int capacityInUnits = 10;
        BigDecimal unit = BigDecimal.valueOf(1_000);
        int attempts = 100;

        Wallet wallet = walletService.credit(WalletOwnerType.ORGANIZATION, organizationId,
                unit.multiply(BigDecimal.valueOf(capacityInUnits)), ReferenceType.PAYMENT, null,
                "test-deposit:" + UUID.randomUUID());

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficientBalance = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            UUID referenceId = UUID.randomUUID();
            jobs.add(() -> {
                ready.countDown();
                start.await();
                try {
                    walletService.reserve(WalletOwnerType.ORGANIZATION, organizationId, unit,
                            ReferenceType.CAMPAIGN_BUDGET, referenceId, "test-concurrent-reserve:" + referenceId);
                    succeeded.incrementAndGet();
                } catch (BusinessException e) {
                    if ("INSUFFICIENT_BALANCE".equals(e.getCode())) {
                        insufficientBalance.incrementAndGet();
                    } else {
                        otherFailures.incrementAndGet();
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> job : jobs) {
            futures.add(pool.submit(job));
        }
        ready.await();
        start.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(otherFailures.get()).as("unexpected failure reasons").isZero();
        assertThat(succeeded.get()).isEqualTo(capacityInUnits);
        assertThat(insufficientBalance.get()).isEqualTo(attempts - capacityInUnits);

        Wallet reloaded = walletService.getWallet(wallet.getId());
        assertThat(reloaded.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded.getReservedBalance()).isEqualByComparingTo(unit.multiply(BigDecimal.valueOf(capacityInUnits)));
        assertLedgerMatchesLiveBalance(reloaded);
    }
}
