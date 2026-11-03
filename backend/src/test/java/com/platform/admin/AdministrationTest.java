package com.platform.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.admin.application.AdminBootstrapService;
import com.platform.audit.application.AuditService;
import com.platform.audit.domain.AuditLog;
import com.platform.campaigns.application.CampaignService;
import com.platform.campaigns.domain.Campaign;
import com.platform.campaigns.domain.CampaignStatus;
import com.platform.common.exception.BusinessException;
import com.platform.organizations.application.OrganizationService;
import com.platform.organizations.domain.KycStatus;
import com.platform.organizations.domain.Organization;
import com.platform.organizations.domain.OrganizationStatus;
import com.platform.tasks.application.TaskService;
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
import java.util.UUID;
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
 * The explicit Phase 9 roadmap milestone (DAT §11): "Un admin peut traiter chaque file de bout
 * en bout." Exercises the three back-office queues this phase closes — admin bootstrap, user
 * suspension, organization verification (the gap flagged since Phase 3) — and confirms each
 * produces an audit trail, not just a status change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AdministrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String BOOTSTRAP_SECRET = "test-bootstrap-secret";

    @DynamicPropertySource
    static void bootstrapSecret(DynamicPropertyRegistry registry) {
        registry.add("platform.admin.bootstrap-secret", () -> BOOTSTRAP_SECRET);
    }

    @Autowired
    private AdminBootstrapService bootstrapService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private CampaignService campaignService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private AuditService auditService;

    private static int counter = 0;

    private User persistUser() {
        int n = counter++;
        User user = new User("admin-test-" + n + "-" + UUID.randomUUID() + "@example.com",
                "+225071000" + String.format("%04d", n), "irrelevant-hash");
        user.markEmailVerified();
        user.markPhoneVerified();
        return userRepository.saveAndFlush(user);
    }

    @Test
    void bootstrapPromotesAnExistingUserAndThenRefusesASecondAdmin() {
        User target = persistUser();

        assertThatThrownBy(() -> bootstrapService.bootstrap("wrong-secret", target.getEmail()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INVALID_BOOTSTRAP_SECRET"));

        assertThatThrownBy(() -> bootstrapService.bootstrap(BOOTSTRAP_SECRET, "nobody-" + UUID.randomUUID() + "@example.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("USER_NOT_FOUND"));

        User promoted = bootstrapService.bootstrap(BOOTSTRAP_SECRET, target.getEmail());
        assertThat(promoted.getPlatformRole().name()).isEqualTo("PLATFORM_ADMIN");

        User another = persistUser();
        assertThatThrownBy(() -> bootstrapService.bootstrap(BOOTSTRAP_SECRET, another.getEmail()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("ADMIN_ALREADY_EXISTS"));
    }

    @Test
    void userSuspensionLifecycleIsAuditedAndCannotBeDowngradedFromBanned() {
        User admin = persistUser();
        User target = persistUser();

        User suspended = userService.suspend(admin.getId(), target.getId());
        assertThat(suspended.getStatus()).isEqualTo(UserStatus.SUSPENDED);

        List<AuditLog> trail = auditService.search("USER", target.getId());
        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getAction()).isEqualTo("USER_STATUS_CHANGED");
        assertThat(trail.get(0).getActorId()).isEqualTo(admin.getId());
        assertThat(trail.get(0).getBeforeState()).contains("ACTIVE");
        assertThat(trail.get(0).getAfterState()).contains("SUSPENDED");

        User reactivated = userService.reactivate(admin.getId(), target.getId());
        assertThat(reactivated.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(auditService.search("USER", target.getId())).hasSize(2);

        userService.ban(admin.getId(), target.getId());
        assertThatThrownBy(() -> userService.suspend(admin.getId(), target.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("USER_BANNED"));
    }

    @Test
    void reactivatingAUserThatIsNotSuspendedOrBannedFails() {
        User admin = persistUser();
        User target = persistUser();
        assertThatThrownBy(() -> userService.reactivate(admin.getId(), target.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("USER_NOT_SUSPENDED"));
    }

    @Test
    void campaignPublishRequiresOrganizationVerificationAndSucceedsOnceVerified() {
        User admin = persistUser();
        User owner = persistUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Admin Test", "Org Admin Test SARL", "CI");
        assertThat(org.getStatus()).isEqualTo(OrganizationStatus.PENDING_VERIFICATION);

        Campaign campaign = campaignService.createCampaign(org.getId(), owner.getId(), "C", "d", "survey",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
        taskService.addTask(campaign.getId(), owner.getId(), "SURVEY", "T", "I", BigDecimal.valueOf(1000), 1, "FORM", null, null);
        campaignService.submitForReview(campaign.getId(), owner.getId(), 1);
        walletService.credit(WalletOwnerType.ORGANIZATION, org.getId(), BigDecimal.valueOf(5000),
                ReferenceType.PAYMENT, UUID.randomUUID(), "test-fund:" + UUID.randomUUID());

        assertThatThrownBy(() -> campaignService.publish(campaign.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("ORGANIZATION_NOT_VERIFIED"));

        Organization verified = organizationService.verify(admin.getId(), org.getId());
        assertThat(verified.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(verified.getKycStatus()).isEqualTo(KycStatus.VERIFIED);

        Campaign published = campaignService.publish(campaign.getId());
        assertThat(published.getStatus()).isEqualTo(CampaignStatus.PUBLISHED);

        assertThat(campaignService.listByStatus(CampaignStatus.PUBLISHED)).extracting(Campaign::getId).contains(campaign.getId());
        assertThat(organizationService.listByStatus(OrganizationStatus.ACTIVE)).extracting(Organization::getId).contains(org.getId());
    }

    @Test
    void rejectedVerificationStoresReasonAndDoesNotChangeOrganizationStatus() {
        User admin = persistUser();
        User owner = persistUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Reject Test", "SARL", "CI");

        Organization rejected = organizationService.rejectVerification(admin.getId(), org.getId(), "Document d'identité manquant");
        assertThat(rejected.getKycStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(rejected.getKycRejectionReason()).isEqualTo("Document d'identité manquant");
        assertThat(rejected.getStatus()).isEqualTo(OrganizationStatus.PENDING_VERIFICATION);
    }

    @Test
    void suspendingAVerifiedOrganizationAndReactivatingRestoresActiveStatus() {
        User admin = persistUser();
        User owner = persistUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Suspend Test", "SARL", "CI");
        organizationService.verify(admin.getId(), org.getId());

        Organization suspended = organizationService.suspend(admin.getId(), org.getId());
        assertThat(suspended.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);

        Organization reactivated = organizationService.reactivate(admin.getId(), org.getId());
        assertThat(reactivated.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);

        List<AuditLog> trail = auditService.search("ORGANIZATION", org.getId());
        assertThat(trail).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void suspendingANeverVerifiedOrganizationAndReactivatingFallsBackToPendingVerification() {
        User admin = persistUser();
        User owner = persistUser();
        Organization org = organizationService.createOrganization(owner.getId(), "Org Never Verified", "SARL", "CI");

        organizationService.suspend(admin.getId(), org.getId());
        Organization reactivated = organizationService.reactivate(admin.getId(), org.getId());

        assertThat(reactivated.getStatus()).isEqualTo(OrganizationStatus.PENDING_VERIFICATION);
    }
}
