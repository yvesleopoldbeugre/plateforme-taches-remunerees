package com.platform.auth.api;

import com.platform.auth.api.dto.LoginRequest;
import com.platform.auth.api.dto.MfaChallengeResponse;
import com.platform.auth.api.dto.PasswordResetConfirmRequest;
import com.platform.auth.api.dto.PasswordResetRequestRequest;
import com.platform.auth.api.dto.RefreshTokenRequest;
import com.platform.auth.api.dto.RegisterRequest;
import com.platform.auth.api.dto.RegisterResponse;
import com.platform.auth.api.dto.ResendEmailRequest;
import com.platform.auth.api.dto.SessionsRevokedResponse;
import com.platform.auth.api.dto.TokenResponse;
import com.platform.auth.api.dto.VerifyEmailRequest;
import com.platform.auth.api.dto.VerifyPhoneRequest;
import com.platform.auth.application.LoginService;
import com.platform.auth.application.LoginService.LoginResult;
import com.platform.auth.application.PasswordResetService;
import com.platform.auth.application.RefreshTokenService;
import com.platform.auth.application.RegisterService;
import com.platform.auth.application.VerificationService;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterService registerService;
    private final LoginService loginService;
    private final VerificationService verificationService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(RegisterService registerService, LoginService loginService,
                           VerificationService verificationService, UserService userService,
                           PasswordResetService passwordResetService, RefreshTokenService refreshTokenService) {
        this.registerService = registerService;
        this.loginService = loginService;
        this.verificationService = verificationService;
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerService.register(request.email(), request.phone(), request.password(),
                request.fullName(), request.country());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.of(user));
    }

    /** Returns a {@link TokenResponse} normally, or a {@link MfaChallengeResponse} when the
     *  account has MFA enabled — see {@code LoginService}'s javadoc for the two-step flow. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request.email(), request.password());
        if (result.mfaRequired()) {
            return ResponseEntity.ok(new MfaChallengeResponse(true, result.mfaChallengeToken()));
        }
        return ResponseEntity.ok(TokenResponse.of(result));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.of(loginService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        loginService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** "Révocation de toutes les sessions d'un coup" (deferred since Phase 2) — the current
     *  session's own access token keeps working until it naturally expires (≤15 min); only
     *  refresh tokens are revoked, so every *other* session stops being renewable immediately. */
    @PostMapping("/sessions/revoke-all")
    public SessionsRevokedResponse revokeAllSessions(Authentication authentication) {
        int revoked = refreshTokenService.revokeAllForUser(currentUserId(authentication));
        return new SessionsRevokedResponse(revoked);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verificationService.verifyEmail(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendEmailVerification(@Valid @RequestBody ResendEmailRequest request) {
        verificationService.resendEmailVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/verify-phone")
    public ResponseEntity<Void> verifyPhone(Authentication authentication, @Valid @RequestBody VerifyPhoneRequest request) {
        verificationService.verifyPhoneOtp(currentUserId(authentication), request.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-phone/resend")
    public ResponseEntity<Void> resendPhoneOtp(Authentication authentication) {
        verificationService.issuePhoneOtp(userService.getById(currentUserId(authentication)));
        return ResponseEntity.accepted().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
