package com.platform.auth.api;

import com.platform.auth.api.dto.MfaCodeRequest;
import com.platform.auth.api.dto.MfaLoginVerifyRequest;
import com.platform.auth.api.dto.MfaSetupResponse;
import com.platform.auth.api.dto.MfaStatusResponse;
import com.platform.auth.api.dto.TokenResponse;
import com.platform.auth.application.LoginService;
import com.platform.auth.application.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /setup}, {@code /enable} and {@code /disable} are self-service and require the normal
 * Bearer session (a user already past login managing their own second factor). {@code
 * /login-verify} is the one public exception — it completes a login that {@code AuthController}
 * deliberately withheld tokens for; see {@code SecurityConfig}'s permitAll entry.
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@Tag(name = "MFA (double authentification)", description = "Activation, désactivation et vérification "
        + "du second facteur TOTP — optionnel pour USER, obligatoire pour PLATFORM_ADMIN/OPERATOR/"
        + "ORGANIZATION_OWNER (CDCT §8).")
public class MfaController {

    private final MfaService mfaService;
    private final LoginService loginService;

    public MfaController(MfaService mfaService, LoginService loginService) {
        this.mfaService = mfaService;
        this.loginService = loginService;
    }

    @Operation(summary = "Démarrer l'enrôlement MFA", description = "Génère un secret TOTP et "
            + "l'enregistre ; le MFA reste désactivé tant que /enable n'a pas confirmé un code valide. "
            + "Renvoie aussi l'URI otpauth:// à convertir en QR code côté client.")
    @PostMapping("/setup")
    public MfaSetupResponse setup(Authentication authentication) {
        return MfaSetupResponse.of(mfaService.setup(currentUserId(authentication)));
    }

    @Operation(summary = "Confirmer et activer le MFA", description = "Vérifie le code TOTP contre le "
            + "secret généré par /setup et active le MFA.")
    @PostMapping("/enable")
    public MfaStatusResponse enable(Authentication authentication, @Valid @RequestBody MfaCodeRequest request) {
        mfaService.enable(currentUserId(authentication), request.code());
        return new MfaStatusResponse(true);
    }

    @Operation(summary = "Désactiver le MFA", description = "Refusé (409 MFA_MANDATORY_FOR_ROLE) si le "
            + "rôle de l'utilisateur ou son statut de propriétaire d'organisation rend le MFA obligatoire.")
    @PostMapping("/disable")
    public MfaStatusResponse disable(Authentication authentication, @Valid @RequestBody MfaCodeRequest request) {
        mfaService.disable(currentUserId(authentication), request.code());
        return new MfaStatusResponse(false);
    }

    @Operation(summary = "Terminer la connexion avec un code TOTP", description = "Second temps de la "
            + "connexion quand /auth/login a renvoyé mfaRequired:true. Un code faux ne consomme pas le "
            + "challenge — il reste utilisable pour une nouvelle tentative jusqu'à expiration (5 min).")
    @PostMapping("/login-verify")
    public TokenResponse loginVerify(@Valid @RequestBody MfaLoginVerifyRequest request) {
        return TokenResponse.of(loginService.verifyMfaAndLogin(request.mfaChallengeToken(), request.code()));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
