package com.platform.participations;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignStatus;
import com.platform.campaigns.infrastructure.CampaignRepository;
import com.platform.common.exception.BusinessException;
import com.platform.organizations.domain.Organization;
import com.platform.organizations.infrastructure.OrganizationRepository;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.ParticipationStatus;
import com.platform.participations.domain.TaskParticipation;
import com.platform.participations.infrastructure.TaskParticipationRepository;
import com.platform.tasks.domain.Task;
import com.platform.tasks.infrastructure.TaskRepository;
import com.platform.users.domain.User;
import com.platform.users.infrastructure.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises BR-020, BR-021 and BR-022 under real concurrency against a real PostgreSQL instance —
 * this is the explicit Phase 5 milestone from the DAT roadmap (§11): "BR-020/021/022 vérifiées
 * par tests de concurrence."
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ParticipationConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void hikariPoolSize(DynamicPropertyRegistry registry) {
        // Default pool size (10) would serialize most of the 100 concurrent calls below and
        // mask a possible race — give the test room to actually run them in parallel.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50");
    }

    @Autowired
    private ParticipationService participationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskParticipationRepository participationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User persistVerifiedUser(String email, String phone) {
        User user = new User(email, phone, "irrelevant-hash");
        user.markEmailVerified();
        user.markPhoneVerified();
        return userRepository.saveAndFlush(user);
    }

    /** A campaign+task fixture in PUBLISHED/ACTIVE state, built directly via repositories —
     *  this test isolates the participation concurrency behaviour, not the campaign lifecycle
     *  already covered by Phase 4's tests. */
    private Task publishedTaskWithCapacity(int maxParticipants) {
        User owner = persistVerifiedUser("owner-" + UUID.randomUUID() + "@example.com", "+225" + randomPhoneSuffix());
        Organization org = organizationRepository.saveAndFlush(new Organization("Org " + UUID.randomUUID(), null, "CI", owner.getId()));
        Campaign campaign = new Campaign(org.getId(), "Campaign", "desc", "survey",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), owner.getId());
        campaign = campaignRepository.saveAndFlush(campaign);
        jdbcTemplate.update("UPDATE campaigns SET status = ? WHERE id = ?", CampaignStatus.PUBLISHED.name(), campaign.getId());

        Task task = new Task(campaign.getId(), "SURVEY", "Task", "Instructions", BigDecimal.valueOf(100),
                maxParticipants, "FORM", null, 1440);
        return taskRepository.saveAndFlush(task);
    }

    private static int phoneCounter = 0;

    private static synchronized String randomPhoneSuffix() {
        return String.format("%09d", phoneCounter++);
    }

    @Test
    void exactlyMaxParticipantsSucceedUnderConcurrency() throws Exception {
        int capacity = 10;
        int attempts = 100;
        Task task = publishedTaskWithCapacity(capacity);

        List<User> users = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            users.add(persistVerifiedUser("racer-" + i + "-" + UUID.randomUUID() + "@example.com", "+225" + randomPhoneSuffix()));
        }

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger taskFull = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        // Must have >= attempts threads: every job blocks on `start` after counting down on
        // `ready`, so a smaller pool would starve-deadlock (queued jobs never get a thread to
        // reach the barrier, the ones holding threads never get released).
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        List<Callable<Void>> jobs = new ArrayList<>();
        for (User user : users) {
            jobs.add(() -> {
                ready.countDown();
                start.await();
                try {
                    participationService.participate(task.getId(), user.getId());
                    succeeded.incrementAndGet();
                } catch (BusinessException e) {
                    if ("TASK_FULL".equals(e.getCode())) {
                        taskFull.incrementAndGet();
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
        assertThat(succeeded.get()).isEqualTo(capacity);
        assertThat(taskFull.get()).isEqualTo(attempts - capacity);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getCurrentParticipants()).isEqualTo(capacity);
        assertThat(participationRepository.findByTaskId(task.getId())).hasSize(capacity);
    }

    @Test
    void onlyOneActiveParticipationPerUserUnderConcurrency() throws Exception {
        int attempts = 20;
        Task task = publishedTaskWithCapacity(50); // ample headroom: isolates BR-020 from BR-022
        User user = persistVerifiedUser("same-user-" + UUID.randomUUID() + "@example.com", "+225" + randomPhoneSuffix());

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger alreadyParticipating = new AtomicInteger();
        AtomicInteger otherFailures = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    participationService.participate(task.getId(), user.getId());
                    succeeded.incrementAndGet();
                } catch (BusinessException e) {
                    if ("ALREADY_PARTICIPATING".equals(e.getCode())) {
                        alreadyParticipating.incrementAndGet();
                    } else {
                        otherFailures.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(otherFailures.get()).as("unexpected failure reasons").isZero();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(alreadyParticipating.get()).isEqualTo(attempts - 1);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getCurrentParticipants()).isEqualTo(1);
    }

    @Test
    void expiredReservationReleasesSlot() {
        Task task = publishedTaskWithCapacity(1);
        User user = persistVerifiedUser("expiry-" + UUID.randomUUID() + "@example.com", "+225" + randomPhoneSuffix());

        TaskParticipation participation = participationService.participate(task.getId(), user.getId());
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);

        jdbcTemplate.update("UPDATE task_participations SET expires_at = now() - interval '1 hour' WHERE id = ?",
                participation.getId());

        int expiredCount = participationService.expireOverdueReservations();
        assertThat(expiredCount).isEqualTo(1);

        TaskParticipation reloaded = participationRepository.findById(participation.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ParticipationStatus.EXPIRED);
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getCurrentParticipants()).isZero();

        // The slot is genuinely free again, not just marked as such.
        User anotherUser = persistVerifiedUser("after-expiry-" + UUID.randomUUID() + "@example.com", "+225" + randomPhoneSuffix());
        TaskParticipation next = participationService.participate(task.getId(), anotherUser.getId());
        assertThat(next.getStatus()).isEqualTo(ParticipationStatus.RESERVED);
    }
}
