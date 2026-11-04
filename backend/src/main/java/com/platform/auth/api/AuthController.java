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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentification", description = "Inscription, connexion, rafraîchissement et "
        + "révocation de session, réinitialisation de mot de passe, vérification email/téléphone (CDCT §8).")
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

    @Operation(summary = "Créer un compte", description = "UC-01/BR-001 — crée le compte et déclenche "
            + "l'envoi du lien de vérification email et du code OTP par SMS.")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerService.register(request.email(), request.phone(), request.password(),
                request.fullName(), request.country());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisterResponse.of(user));
    }

    @Operation(summary = "Se connecter", description = "Renvoie les jetons normalement, ou "
            + "{mfaRequired:true, mfaChallengeToken} si le compte a le MFA activé — voir le tag MFA "
            + "pour la suite du flux à deux temps.")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request.email(), request.password());
        if (result.mfaRequired()) {
            return ResponseEntity.ok(new MfaChallengeResponse(true, result.mfaChallengeToken()));
        }
        return ResponseEntity.ok(TokenResponse.of(result));
    }

    @Operation(summary = "Rafraîchir la session", description = "Fait tourner le jeton de rafraîchissement "
            + "(usage unique) et renvoie un nouveau jeton d'accès.")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.of(loginService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Se déconnecter", description = "Révoque uniquement le jeton de rafraîchissement fourni.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        loginService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Révoquer toutes les sessions d'un coup", description = "Révoque tous les jetons "
            + "de rafraîchissement du compte connecté ; le jeton d'accès déjà émis reste valide jusqu'à "
            + "expiration naturelle (≤15 min), mais aucune autre session ne peut plus se renouveler.")
    @PostMapping("/sessions/revoke-all")
    public SessionsRevokedResponse revokeAllSessions(Authentication authentication) {
        int revoked = refreshTokenService.revokeAllForUser(currentUserId(authentication));
        return new SessionsRevokedResponse(revoked);
    }

    @Operation(summary = "Demander une réinitialisation de mot de passe", description = "Répond toujours "
            + "202, que l'email existe ou non (pas de fuite d'existence de compte) ; envoie un lien à usage "
            + "unique si le compte existe.")
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Confirmer la réinitialisation de mot de passe", description = "Change le mot de "
            + "passe et révoque aussi toutes les sessions existantes du compte.")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Vérifier l'adresse email", description = "Valide le lien à usage unique envoyé à l'inscription.")
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verificationService.verifyEmail(request.token());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Renvoyer le lien de vérification email")
    @PostMapping("/verify-email/resend")
    public ResponseEntity<Void> resendEmailVerification(@Valid @RequestBody ResendEmailRequest request) {
        verificationService.resendEmailVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Vérifier le numéro de téléphone", description = "Valide le code OTP à 6 chiffres reçu par SMS.")
    @PostMapping("/verify-phone")
    public ResponseEntity<Void> verifyPhone(Authentication authentication, @Valid @RequestBody VerifyPhoneRequest request) {
        verificationService.verifyPhoneOtp(currentUserId(authentication), request.code());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Renvoyer le code OTP par SMS")
    @PostMapping("/verify-phone/resend")
    public ResponseEntity<Void> resendPhoneOtp(Authentication authentication) {
        verificationService.issuePhoneOtp(userService.getById(currentUserId(authentication)));
        return ResponseEntity.accepted().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
