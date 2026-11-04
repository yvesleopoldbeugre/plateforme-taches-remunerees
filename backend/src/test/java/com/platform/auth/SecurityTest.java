package com.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.auth.application.LoginService;
import com.platform.auth.application.LoginService.LoginResult;
import com.platform.auth.application.MfaService;
import com.platform.auth.application.PasswordResetService;
import com.platform.auth.application.RefreshTokenService;
import com.platform.auth.application.RegisterService;
import com.platform.auth.application.TotpService;
import com.platform.auth.application.VerificationNotifier;
import com.platform.common.exception.BusinessException;
import com.platform.organizations.application.OrganizationService;
import com.platform.users.application.UserService;
import com.platform.users.domain.PlatformRole;
import com.platform.users.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Phase 12 (sécurité des comptes) — MFA/TOTP, réinitialisation de mot de passe, révocation
 * globale des sessions. This phase has no single DAT-given validation criterion (it exists
 * entirely in the "explicitement reporté" lists of Phases 2-9), so this class covers all three
 * features directly against the real services, not just the happy path.
 *
 * <p>The first test class in this project that actually needs a reachable Redis — every prior
 * phase's fixtures happened to bypass {@code auth}'s Redis-touching code paths entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RegisterService registerService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private MfaService mfaService;
    @Autowired
    private TotpService totpService;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private CapturingVerificationNotifier capturingNotifier;

    /** Replaces {@code LoggingVerificationNotifier} so the test can read back the token a real
     *  caller would only ever see inside an email — the same injection point production code uses. */
    @TestConfiguration
    static class NotifierTestConfig {
        @Bean
        @Primary
        CapturingVerificationNotifier capturingVerificationNotifier() {
            return new CapturingVerificationNotifier();
        }
    }

    static class CapturingVerificationNotifier implements VerificationNotifier {
        volatile String lastPasswordResetToken;

        @Override
        public void sendEmailVerification(String email, String token) {
        }

        @Override
        public void sendPhoneOtp(String phone, String code) {
        }

        @Override
        public void sendPasswordResetEmail(String email, String token) {
            this.lastPasswordResetToken = token;
        }
    }

    private static int counter = 0;

    private User registerUser(String password) {
        int n = counter++;
        return registerService.register("security-test-" + n + "-" + UUID.randomUUID() + "@example.com",
                "+225074000" + String.format("%04d", n), password, "Security Test " + n, "CI");
    }

    @Test
    void mfaSetupEnableAndLoginChallengeRoundTrip() {
        User user = registerUser("Str0ngPass!2026");

        MfaService.MfaSetup setup = mfaService.setup(user.getId());
        assertThat(setup.secret()).isNotBlank();
        assertThat(setup.provisioningUri()).contains("otpauth://totp/").contains(setup.secret());

        assertThatThrownBy(() -> mfaService.enable(user.getId(), "000000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INVALID_MFA_CODE"));

        mfaService.enable(user.getId(), totpService.currentCode(setup.secret()));
        assertThat(userService.getById(user.getId()).isMfaEnabled()).isTrue();

        // Login now withholds tokens and hands back a challenge instead.
        LoginResult challenge = loginService.login(user.getEmail(), "Str0ngPass!2026");
        assertThat(challenge.mfaRequired()).isTrue();
        assertThat(challenge.accessToken()).isNull();

        assertThatThrownBy(() -> loginService.verifyMfaAndLogin(challenge.mfaChallengeToken(), "000000"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INVALID_MFA_CODE"));

        LoginResult success = loginService.verifyMfaAndLogin(challenge.mfaChallengeToken(), totpService.currentCode(setup.secret()));
        assertThat(success.mfaRequired()).isFalse();
        assertThat(success.accessToken()).isNotBlank();
        assertThat(success.refreshToken()).isNotBlank();

        // The challenge token is single-use.
        assertThatThrownBy(() -> loginService.verifyMfaAndLogin(challenge.mfaChallengeToken(), totpService.currentCode(setup.secret())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_CHALLENGE_EXPIRED"));
    }

    @Test
    void mfaSetupRecommendedFlagsPlatformAdminAndOrganizationOwnerButNotPlainUser() {
        User plainUser = registerUser("Str0ngPass!2026");
        LoginResult plainLogin = loginService.login(plainUser.getEmail(), "Str0ngPass!2026");
        assertThat(plainLogin.mfaSetupRecommended()).isFalse();

        User admin = registerUser("Str0ngPass!2026");
        userService.grantPlatformRole(admin.getId(), PlatformRole.PLATFORM_ADMIN);
        LoginResult adminLogin = loginService.login(admin.getEmail(), "Str0ngPass!2026");
        assertThat(adminLogin.mfaSetupRecommended()).isTrue();

        User owner = registerUser("Str0ngPass!2026");
        organizationService.createOrganization(owner.getId(), "Org Security Test", "SARL", "CI");
        LoginResult ownerLogin = loginService.login(owner.getEmail(), "Str0ngPass!2026");
        assertThat(ownerLogin.mfaSetupRecommended()).isTrue();
    }

    @Test
    void disableIsBlockedForAMandatoryRoleButAllowedForAPlainUser() {
        User admin = registerUser("Str0ngPass!2026");
        userService.grantPlatformRole(admin.getId(), PlatformRole.PLATFORM_ADMIN);
        MfaService.MfaSetup adminSetup = mfaService.setup(admin.getId());
        mfaService.enable(admin.getId(), totpService.currentCode(adminSetup.secret()));

        assertThatThrownBy(() -> mfaService.disable(admin.getId(), totpService.currentCode(adminSetup.secret())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("MFA_MANDATORY_FOR_ROLE"));

        User plainUser = registerUser("Str0ngPass!2026");
        MfaService.MfaSetup userSetup = mfaService.setup(plainUser.getId());
        mfaService.enable(plainUser.getId(), totpService.currentCode(userSetup.secret()));
        mfaService.disable(plainUser.getId(), totpService.currentCode(userSetup.secret()));
        assertThat(userService.getById(plainUser.getId()).isMfaEnabled()).isFalse();
    }

    @Test
    void passwordResetChangesPasswordAndRevokesExistingSessions() {
        User user = registerUser("OldPassw0rd!2026");
        LoginResult firstLogin = loginService.login(user.getEmail(), "OldPassw0rd!2026");
        String oldRefreshToken = firstLogin.refreshToken();

        // Unknown emails are accepted silently too (no enumeration leak) — nothing to assert
        // beyond "it doesn't throw".
        passwordResetService.requestReset("nobody-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(() -> passwordResetService.confirmReset("not-a-real-token", "NewPassw0rd!2026"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INVALID_TOKEN"));

        passwordResetService.requestReset(user.getEmail());
        String resetToken = capturingNotifier.lastPasswordResetToken;
        assertThat(resetToken).isNotBlank();

        passwordResetService.confirmReset(resetToken, "NewPassw0rd!2026");

        assertThatThrownBy(() -> loginService.login(user.getEmail(), "OldPassw0rd!2026"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INVALID_CREDENTIALS"));
        LoginResult afterReset = loginService.login(user.getEmail(), "NewPassw0rd!2026");
        assertThat(afterReset.accessToken()).isNotBlank();

        // The session that existed before the reset is dead — confirmReset revoked it.
        assertThatThrownBy(() -> refreshTokenService.rotate(oldRefreshToken)).isInstanceOf(BusinessException.class);

        // The token is single-use.
        assertThatThrownBy(() -> passwordResetService.confirmReset(resetToken, "AnotherPassw0rd!2026"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo("INVALID_TOKEN"));
    }

    @Test
    void revokeAllSessionsInvalidatesEveryExistingRefreshToken() {
        User user = registerUser("Str0ngPass!2026");
        String token1 = refreshTokenService.issue(user.getId());
        String token2 = refreshTokenService.issue(user.getId());

        int revoked = refreshTokenService.revokeAllForUser(user.getId());
        assertThat(revoked).isEqualTo(2);

        assertThatThrownBy(() -> refreshTokenService.rotate(token1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> refreshTokenService.rotate(token2)).isInstanceOf(BusinessException.class);
    }

    @Test
    void suspendingAUserRevokesItsExistingRefreshTokens() {
        User admin = registerUser("Str0ngPass!2026");
        User target = registerUser("Str0ngPass!2026");
        String refreshToken = refreshTokenService.issue(target.getId());

        userService.suspend(admin.getId(), target.getId());

        assertThatThrownBy(() -> refreshTokenService.rotate(refreshToken)).isInstanceOf(BusinessException.class);
    }
}
