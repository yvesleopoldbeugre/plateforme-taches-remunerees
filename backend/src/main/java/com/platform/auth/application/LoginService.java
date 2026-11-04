package com.platform.auth.application;

import com.platform.common.exception.ForbiddenException;
import com.platform.common.exception.UnauthorizedException;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * UC — login. Enforces BR-002 (a suspended/banned account cannot authenticate). Phase 12 adds
 * the TOTP step (CDCT §8): when {@code mfaEnabled}, password-verified-but-not-yet-MFA-verified
 * is a real intermediate state — {@link #login} withholds both tokens and hands back a
 * short-lived challenge instead; {@link #verifyMfaAndLogin} is the second step.
 */
@Service
public class LoginService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;

    public LoginService(UserService userService, PasswordEncoder passwordEncoder,
                         JwtService jwtService, RefreshTokenService refreshTokenService, MfaService mfaService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.mfaService = mfaService;
    }

    public LoginResult login(String email, String rawPassword) {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Email ou mot de passe incorrect."));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email ou mot de passe incorrect.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Ce compte est suspendu.");
        }

        if (user.isMfaEnabled()) {
            return LoginResult.mfaChallenge(mfaService.issueLoginChallenge(user.getId()));
        }

        return issueSession(user);
    }

    /**
     * Second step of the TOTP login flow — see the class javadoc. A wrong code leaves the
     * challenge intact (retry without a fresh login); only a correct one invalidates it.
     */
    public LoginResult verifyMfaAndLogin(String challengeToken, String code) {
        UUID userId = mfaService.peekLoginChallenge(challengeToken);
        if (!mfaService.verifyLoginCode(userId, code)) {
            throw new UnauthorizedException("INVALID_MFA_CODE", "Code TOTP invalide.");
        }
        mfaService.invalidateLoginChallenge(challengeToken);
        User user = userService.getById(userId);
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Ce compte est suspendu.");
        }
        return issueSession(user);
    }

    public LoginResult refresh(String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken);
        User user = userService.getById(rotated.userId());
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Ce compte est suspendu.");
        }
        String accessToken = jwtService.generateAccessToken(user.getId(), authoritiesOf(user));
        return LoginResult.success(user, accessToken, rotated.token(), jwtService.getAccessTokenTtl(), false);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private LoginResult issueSession(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), authoritiesOf(user));
        String refreshToken = refreshTokenService.issue(user.getId());
        boolean mfaSetupRecommended = !user.isMfaEnabled() && mfaService.isMandatoryForRole(user);
        return LoginResult.success(user, accessToken, refreshToken, jwtService.getAccessTokenTtl(), mfaSetupRecommended);
    }

    static List<String> authoritiesOf(User user) {
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_USER");
        if (user.getPlatformRole() != null) {
            authorities.add("ROLE_" + user.getPlatformRole().name());
        }
        return authorities;
    }

    /**
     * {@code user}/{@code accessToken}/{@code refreshToken}/{@code expiresIn} are null exactly
     * when {@code mfaRequired} is true — the caller must hit {@code verifyMfaAndLogin} with
     * {@code mfaChallengeToken} before getting a real session.
     */
    public record LoginResult(User user, String accessToken, String refreshToken, Duration expiresIn,
                               boolean mfaRequired, String mfaChallengeToken, boolean mfaSetupRecommended) {

        public static LoginResult success(User user, String accessToken, String refreshToken, Duration expiresIn,
                                           boolean mfaSetupRecommended) {
            return new LoginResult(user, accessToken, refreshToken, expiresIn, false, null, mfaSetupRecommended);
        }

        public static LoginResult mfaChallenge(String challengeToken) {
            return new LoginResult(null, null, null, null, true, challengeToken, false);
        }
    }
}
