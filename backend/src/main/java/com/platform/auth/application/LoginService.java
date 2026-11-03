package com.platform.auth.application;

import com.platform.common.exception.ForbiddenException;
import com.platform.common.exception.UnauthorizedException;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** UC — login. Enforces BR-002 (a suspended/banned account cannot authenticate). */
@Service
public class LoginService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public LoginService(UserService userService, PasswordEncoder passwordEncoder,
                         JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
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

        String accessToken = jwtService.generateAccessToken(user.getId(), authoritiesOf(user));
        String refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResult(user, accessToken, refreshToken, jwtService.getAccessTokenTtl());
    }

    public LoginResult refresh(String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken);
        User user = userService.getById(rotated.userId());
        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Ce compte est suspendu.");
        }
        String accessToken = jwtService.generateAccessToken(user.getId(), authoritiesOf(user));
        return new LoginResult(user, accessToken, rotated.token(), jwtService.getAccessTokenTtl());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    static List<String> authoritiesOf(User user) {
        List<String> authorities = new ArrayList<>();
        authorities.add("ROLE_USER");
        if (user.getPlatformRole() != null) {
            authorities.add("ROLE_" + user.getPlatformRole().name());
        }
        return authorities;
    }

    public record LoginResult(User user, String accessToken, String refreshToken, Duration expiresIn) {
    }
}
