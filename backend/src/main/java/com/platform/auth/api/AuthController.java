package com.platform.auth.api;

import com.platform.auth.api.dto.LoginRequest;
import com.platform.auth.api.dto.RefreshTokenRequest;
import com.platform.auth.api.dto.RegisterRequest;
import com.platform.auth.api.dto.RegisterResponse;
import com.platform.auth.api.dto.ResendEmailRequest;
import com.platform.auth.api.dto.TokenResponse;
import com.platform.auth.api.dto.VerifyEmailRequest;
import com.platform.auth.api.dto.VerifyPhoneRequest;
import com.platform.auth.application.LoginService;
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

    public AuthController(RegisterService registerService, LoginService loginService,
                           VerificationService verificationService, UserService userService) {
        this.registerService = registerService;
        this.loginService = loginService;
        this.verificationService = verificationService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerService.register(request.email(), request.phone(), request.password(),
                request.fullName(), request.country());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.of(user));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.of(loginService.login(request.email(), request.password()));
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
