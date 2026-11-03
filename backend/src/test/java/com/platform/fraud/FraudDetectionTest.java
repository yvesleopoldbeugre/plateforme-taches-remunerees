package com.platform.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignStatus;
import com.platform.campaigns.infrastructure.CampaignRepository;
import com.platform.common.exception.BusinessException;
import com.platform.fraud.application.FraudService;
import com.platform.fraud.domain.FraudEvent;
import com.platform.fraud.domain.FraudEventStatus;
import com.platform.fraud.domain.FraudEventType;
import com.platform.organizations.domain.Organization;
import com.platform.organizations.infrastructure.OrganizationRepository;
import com.platform.participations.application.ParticipationService;
import com.platform.participations.domain.ParticipationStatus;
import com.platform.participations.domain.TaskParticipation;
import com.platform.tasks.domain.Task;
import com.platform.tasks.infrastructure.TaskRepository;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import com.platform.users.infrastructure.UserRepository;
import com.platform.wallet.application.WalletService;
import com.platform.wallet.domain.ReferenceType;
import com.platform.wallet.domain.WalletOwnerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The explicit Phase 10 roadmap milestone (DAT §11): "Une soumission dupliquée génère un
 * événement." Also exercises the UC-22 decision flow this phase builds around that one detector
 * (review/dismiss/confirm, confirm actually suspending the user) and the control case (a
 * non-duplicate submission auto-validates normally and raises no fraud event at all).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FraudDetectionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ParticipationService participationService;
    @Autowired
    private FraudService fraudService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private WalletService walletService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static int counter = 0;

    private User persistVerifiedUser() {
        int n = counter++;
        User user = new User("fraud-test-" + n + "-" + UUID.randomUUID() + "@example.com",
                "+225072000" + String.format("%04d", n), "irrelevant-hash");
        user.markEmailVerified();
        user.markPhoneVerified();
        return userRepository.saveAndFlush(user);
    }

    /** Mirrors {@code ParticipationConcurrencyTest}'s fixture: PUBLISHED set directly in SQL
     *  (bypassing CampaignService#publish and its Phase 9 verification gate, which this test
     *  isn't exercising), with the organization's wallet funded to cover the reward like
     *  `publish` itself would reserve. */
    private Task publishedTaskWithCapacity(int maxParticipants, String autoValidationRules) {
        User owner = persistVerifiedUser();
        Organization org = organizationRepository.saveAndFlush(new Organization("Org " + UUID.randomUUID(), null, "CI", owner.getId()));
        Campaign campaign = new Campaign(org.getId(), "Campaign", "desc", "survey",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), owner.getId());
        campaign = campaignRepository.saveAndFlush(campaign);
        jdbcTemplate.update("UPDATE campaigns SET status = ? WHERE id = ?", CampaignStatus.PUBLISHED.name(), campaign.getId());

        Task task = new Task(campaign.getId(), "SURVEY", "Task", "Instructions", BigDecimal.valueOf(100),
                maxParticipants, "FORM", autoValidationRules, 1440);
        task = taskRepository.saveAndFlush(task);

        walletService.credit(WalletOwnerType.ORGANIZATION, org.getId(), task.totalBudget(),
                ReferenceType.PAYMENT, UUID.randomUUID(), "test-fund:" + UUID.randomUUID());
        walletService.reserve(WalletOwnerType.ORGANIZATION, org.getId(), task.totalBudget(),
                ReferenceType.CAMPAIGN_BUDGET, campaign.getId(), "test-reserve:" + campaign.getId());
        return task;
    }

    @Test
    void duplicateSubmissionIsRecordedFlaggedAndRaisesAnOpenFraudEvent() {
        Task task = publishedTaskWithCapacity(5, null);
        User first = persistVerifiedUser();
        User second = persistVerifiedUser();
        Map<String, Object> sameContent = Map.of("answer", "42");

        TaskParticipation p1 = participationService.participate(task.getId(), first.getId());
        participationService.submit(p1.getId(), first.getId(), sameContent, null);

        TaskParticipation p2 = participationService.participate(task.getId(), second.getId());
        TaskParticipation submitted = participationService.submit(p2.getId(), second.getId(), sameContent, null);

        // Not rejected — BR-051 revisited: recorded, flagged, falls back to manual review.
        assertThat(submitted.getStatus()).isEqualTo(ParticipationStatus.UNDER_REVIEW);

        List<FraudEvent> open = fraudService.listByStatus(FraudEventStatus.OPEN);
        FraudEvent event = open.stream().filter(e -> e.getUserId().equals(second.getId())).findFirst().orElseThrow();
        assertThat(event.getType()).isEqualTo(FraudEventType.DUPLICATE_SUBMISSION);
        assertThat(event.getCampaignId()).isEqualTo(task.getCampaignId());
        assertThat(event.getReason()).contains("BR-051");
    }

    @Test
    void duplicateSubmissionNeverAutoApprovesEvenWhenRulesWouldOtherwiseMatch() {
        Task task = publishedTaskWithCapacity(5, "{\"requiredFields\": [\"answer\"]}");
        User first = persistVerifiedUser();
        User second = persistVerifiedUser();
        Map<String, Object> sameContent = Map.of("answer", "42");

        TaskParticipation p1 = participationService.participate(task.getId(), first.getId());
        TaskParticipation firstSubmitted = participationService.submit(p1.getId(), first.getId(), sameContent, null);
        // Control: the non-duplicate first submission auto-approves normally under matching rules.
        assertThat(firstSubmitted.getStatus()).isEqualTo(ParticipationStatus.APPROVED);

        TaskParticipation p2 = participationService.participate(task.getId(), second.getId());
        TaskParticipation secondSubmitted = participationService.submit(p2.getId(), second.getId(), sameContent, null);
        // The duplicate never auto-approves, despite satisfying the exact same rule.
        assertThat(secondSubmitted.getStatus()).isEqualTo(ParticipationStatus.UNDER_REVIEW);
    }

    @Test
    void nonDuplicateSubmissionsRaiseNoFraudEvent() {
        Task task = publishedTaskWithCapacity(5, null);
        User user = persistVerifiedUser();
        TaskParticipation p = participationService.participate(task.getId(), user.getId());
        participationService.submit(p.getId(), user.getId(), Map.of("answer", "unique-" + UUID.randomUUID()), null);

        assertThat(fraudService.listByStatus(FraudEventStatus.OPEN)).noneMatch(e -> user.getId().equals(e.getUserId()));
    }

    @Test
    void confirmingAFraudEventSuspendsTheUser() {
        Task task = publishedTaskWithCapacity(5, null);
        User admin = persistVerifiedUser();
        User first = persistVerifiedUser();
        User second = persistVerifiedUser();
        Map<String, Object> sameContent = Map.of("answer", "dup-" + UUID.randomUUID());

        participationService.submit(participationService.participate(task.getId(), first.getId()).getId(), first.getId(), sameContent, null);
        participationService.submit(participationService.participate(task.getId(), second.getId()).getId(), second.getId(), sameContent, null);

        FraudEvent event = fraudService.listByStatus(FraudEventStatus.OPEN).stream()
                .filter(e -> e.getUserId().equals(second.getId())).findFirst().orElseThrow();

        FraudEvent confirmed = fraudService.confirm(admin.getId(), event.getId(), "Comportement confirmé après vérification manuelle");
        assertThat(confirmed.getStatus()).isEqualTo(FraudEventStatus.CONFIRMED);
        assertThat(userService.getById(second.getId()).getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void reviewThenDismissIsAValidPathButDecidingTwiceFails() {
        Task task = publishedTaskWithCapacity(5, null);
        User admin = persistVerifiedUser();
        User first = persistVerifiedUser();
        User second = persistVerifiedUser();
        Map<String, Object> sameContent = Map.of("answer", "dup2-" + UUID.randomUUID());

        participationService.submit(participationService.participate(task.getId(), first.getId()).getId(), first.getId(), sameContent, null);
        participationService.submit(participationService.participate(task.getId(), second.getId()).getId(), second.getId(), sameContent, null);

        FraudEvent event = fraudService.listByStatus(FraudEventStatus.OPEN).stream()
                .filter(e -> e.getUserId().equals(second.getId())).findFirst().orElseThrow();

        FraudEvent reviewed = fraudService.markReviewed(admin.getId(), event.getId(), "Escaladé pour revue approfondie");
        assertThat(reviewed.getStatus()).isEqualTo(FraudEventStatus.REVIEWED);

        FraudEvent dismissed = fraudService.dismiss(admin.getId(), event.getId(), "Faux positif, même réponse légitime deux fois");
        assertThat(dismissed.getStatus()).isEqualTo(FraudEventStatus.DISMISSED);
        assertThat(userService.getById(second.getId()).getStatus()).isNotEqualTo(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> fraudService.confirm(admin.getId(), event.getId(), "trop tard"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("FRAUD_EVENT_ALREADY_DECIDED"));
    }
}
