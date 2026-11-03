package com.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.fraud.application.FraudService;
import com.platform.fraud.domain.FraudRiskLevel;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.Organization;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.TaskParticipation;
import com.platform.statistics.api.dto.OrganizationStatisticsResponse;
import com.platform.statistics.api.dto.PlatformStatisticsResponse;
import com.platform.statistics.api.dto.UserStatisticsResponse;
import com.platform.statistics.application.OrganizationStatisticsService;
import com.platform.statistics.application.PlatformStatisticsService;
import com.platform.statistics.application.UserStatisticsService;
import com.platform.submissions.application.SubmissionService;
import com.platform.submissions.domain.TaskSubmission;
import com.platform.tasks.application.TaskService;
import com.platform.tasks.domain.Task;
import com.platform.users.domain.User;
import com.platform.users.infrastructure.UserRepository;
import com.platform.validation.application.ValidationService;
import com.platform.validation.domain.RejectionReasons;
import com.platform.validation.domain.ValidationDecision;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.WalletOwnerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The explicit Phase 11 roadmap milestone (DAT §11): "Les KPI sont affichés et corrects sur
 * données de test." Drives a real campaign through publish → approve/reject/cancel using the
 * actual services (not raw SQL) so every statistic is checked against a scenario whose expected
 * numbers are known by construction, not against a vague "looks plausible" eyeball.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StatisticsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private CampaignService campaignService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private ParticipationService participationService;
    @Autowired
    private SubmissionService submissionService;
    @Autowired
    private ValidationService validationService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private FraudService fraudService;
    @Autowired
    private OrganizationStatisticsService organizationStatisticsService;
    @Autowired
    private UserStatisticsService userStatisticsService;
    @Autowired
    private PlatformStatisticsService platformStatisticsService;

    private static int counter = 0;

    private User persistVerifiedUser() {
        int n = counter++;
        User user = new User("stats-test-" + n + "-" + UUID.randomUUID() + "@example.com",
                "+225073000" + String.format("%04d", n), "irrelevant-hash");
        user.markEmailVerified();
        user.markPhoneVerified();
        return userRepository.saveAndFlush(user);
    }

    @Test
    void organizationStatisticsReflectApprovedRejectedAndAbandonedParticipations() {
        User admin = persistVerifiedUser();
        User owner = persistVerifiedUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Stats", "SARL", "CI");
        organizationService.verify(admin.getId(), org.getId());

        Campaign campaign = campaignService.createCampaign(org.getId(), owner.getId(), "C", "d", "survey",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
        Task task = taskService.addTask(campaign.getId(), owner.getId(), "SURVEY", "T", "I",
                BigDecimal.valueOf(1000), 5, "FORM", null, null);
        campaignService.submitForReview(campaign.getId(), owner.getId(), 1);
        walletService.credit(WalletOwnerType.ORGANIZATION, org.getId(), BigDecimal.valueOf(10_000),
                ReferenceType.PAYMENT, UUID.randomUUID(), "test-fund:" + UUID.randomUUID());
        campaignService.publish(campaign.getId());

        User approvedWorker = persistVerifiedUser();
        decideOnFreshSubmission(task, approvedWorker, owner.getId(), ValidationDecision.APPROVED, null);

        User rejectedWorker = persistVerifiedUser();
        decideOnFreshSubmission(task, rejectedWorker, owner.getId(), ValidationDecision.REJECTED, RejectionReasons.OTHER);

        User abandoningWorker = persistVerifiedUser();
        TaskParticipation abandoned = participationService.participate(task.getId(), abandoningWorker.getId());
        participationService.cancel(abandoned.getId(), abandoningWorker.getId());

        OrganizationStatisticsResponse stats = organizationStatisticsService.compute(org.getId(), owner.getId());
        assertThat(stats.campaignsCreated()).isEqualTo(1);
        assertThat(stats.campaignsActive()).isEqualTo(1);
        assertThat(stats.totalParticipations()).isEqualTo(3);
        assertThat(stats.validationRate()).isEqualByComparingTo("0.5000");
        assertThat(stats.rejectionRate()).isEqualByComparingTo("0.5000");
        assertThat(stats.abandonRate()).isEqualByComparingTo(BigDecimal.ONE.divide(BigDecimal.valueOf(3), 4, java.math.RoundingMode.HALF_UP));
        assertThat(stats.budgetSpent()).isEqualByComparingTo("1000.00");
        assertThat(stats.averageCostPerParticipation()).isEqualByComparingTo("1000.00");
    }

    @Test
    void userStatisticsDistinguishApprovedFromRejectedAndTrackWalletTotals() {
        User admin = persistVerifiedUser();
        User owner = persistVerifiedUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Stats 2", "SARL", "CI");
        organizationService.verify(admin.getId(), org.getId());
        Campaign campaign = campaignService.createCampaign(org.getId(), owner.getId(), "C", "d", "survey",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
        Task task = taskService.addTask(campaign.getId(), owner.getId(), "SURVEY", "T", "I",
                BigDecimal.valueOf(1500), 5, "FORM", null, null);
        campaignService.submitForReview(campaign.getId(), owner.getId(), 1);
        walletService.credit(WalletOwnerType.ORGANIZATION, org.getId(), BigDecimal.valueOf(10_000),
                ReferenceType.PAYMENT, UUID.randomUUID(), "test-fund:" + UUID.randomUUID());
        campaignService.publish(campaign.getId());

        User approvedWorker = persistVerifiedUser();
        decideOnFreshSubmission(task, approvedWorker, owner.getId(), ValidationDecision.APPROVED, null);
        UserStatisticsResponse approvedStats = userStatisticsService.compute(approvedWorker.getId());
        assertThat(approvedStats.tasksRealized()).isEqualTo(1);
        assertThat(approvedStats.tasksValidated()).isEqualTo(1);
        assertThat(approvedStats.tasksRejected()).isEqualTo(0);
        assertThat(approvedStats.successRate()).isEqualByComparingTo("1.0000");
        assertThat(approvedStats.revenueTotal()).isEqualByComparingTo("1500.00");
        assertThat(approvedStats.revenueAverage()).isEqualByComparingTo("1500.00");

        User rejectedWorker = persistVerifiedUser();
        decideOnFreshSubmission(task, rejectedWorker, owner.getId(), ValidationDecision.REJECTED, RejectionReasons.OTHER);
        UserStatisticsResponse rejectedStats = userStatisticsService.compute(rejectedWorker.getId());
        assertThat(rejectedStats.tasksRealized()).isEqualTo(1);
        assertThat(rejectedStats.tasksValidated()).isEqualTo(0);
        assertThat(rejectedStats.tasksRejected()).isEqualTo(1);
        assertThat(rejectedStats.successRate()).isEqualByComparingTo("0.0000");
        assertThat(rejectedStats.revenueTotal()).isEqualByComparingTo("0");

        User neverSubmittedWorker = persistVerifiedUser();
        participationService.participate(task.getId(), neverSubmittedWorker.getId());
        UserStatisticsResponse untouchedStats = userStatisticsService.compute(neverSubmittedWorker.getId());
        assertThat(untouchedStats.tasksRealized()).isEqualTo(0);
    }

    @Test
    void platformStatisticsReflectActiveUsersFinancialVolumeAndFraudCounts() {
        long usersBefore = platformStatisticsService.compute().activeUsers();
        persistVerifiedUser();
        assertThat(platformStatisticsService.compute().activeUsers()).isEqualTo(usersBefore + 1);

        User admin = persistVerifiedUser();
        User owner = persistVerifiedUser();
        long orgsBefore = platformStatisticsService.compute().activeOrganizations();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Stats 3", "SARL", "CI");
        organizationService.verify(admin.getId(), org.getId());
        assertThat(platformStatisticsService.compute().activeOrganizations()).isEqualTo(orgsBefore + 1);

        BigDecimal volumeBefore = platformStatisticsService.compute().totalFinancialVolume();
        walletService.credit(WalletOwnerType.ORGANIZATION, org.getId(), BigDecimal.valueOf(777),
                ReferenceType.PAYMENT, UUID.randomUUID(), "test-fund:" + UUID.randomUUID());
        BigDecimal volumeAfter = platformStatisticsService.compute().totalFinancialVolume();
        assertThat(volumeAfter.subtract(volumeBefore)).isEqualByComparingTo("777");

        // commissionsCollected is always 0 — BR-044 is explicitly deferred (see the DTO's javadoc).
        assertThat(platformStatisticsService.compute().commissionsCollected()).isEqualByComparingTo("0");

        long mediumBefore = fraudService.countByRiskLevel(FraudRiskLevel.MEDIUM);
        Campaign campaign = campaignService.createCampaign(org.getId(), owner.getId(), "C", "d", "survey",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
        Task task = taskService.addTask(campaign.getId(), owner.getId(), "SURVEY", "T", "I",
                BigDecimal.valueOf(100), 5, "FORM", null, null);
        campaignService.submitForReview(campaign.getId(), owner.getId(), 1);
        walletService.credit(WalletOwnerType.ORGANIZATION, org.getId(), BigDecimal.valueOf(1_000),
                ReferenceType.PAYMENT, UUID.randomUUID(), "test-fund:" + UUID.randomUUID());
        campaignService.publish(campaign.getId());

        User first = persistVerifiedUser();
        User second = persistVerifiedUser();
        Map<String, Object> sameContent = Map.of("answer", "dup-" + UUID.randomUUID());
        participationService.submit(participationService.participate(task.getId(), first.getId()).getId(), first.getId(), sameContent, null);
        participationService.submit(participationService.participate(task.getId(), second.getId()).getId(), second.getId(), sameContent, null);

        assertThat(platformStatisticsService.compute().fraudEventsMediumRisk()).isEqualTo(mediumBefore + 1);
    }

    private void decideOnFreshSubmission(Task task, User worker, UUID reviewerId, ValidationDecision decision, String reasonCode) {
        TaskParticipation participation = participationService.participate(task.getId(), worker.getId());
        participationService.submit(participation.getId(), worker.getId(), Map.of("answer", "unique-" + UUID.randomUUID()), null);
        TaskSubmission submission = submissionService.listByParticipation(participation.getId()).get(0);
        validationService.decide(submission.getId(), reviewerId, decision, reasonCode, decision == ValidationDecision.APPROVED ? null : "test");
    }
}
