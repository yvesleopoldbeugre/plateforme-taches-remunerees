package com.platform.auth.api;

import com.platform.auth.api.dto.MfaCodeRequest;
import com.platform.auth.api.dto.MfaLoginVerifyRequest;
import com.platform.auth.api.dto.MfaSetupResponse;
import com.platform.auth.api.dto.MfaStatusResponse;
import com.platform.auth.api.dto.TokenResponse;
import com.platform.auth.application.LoginService;
import com.platform.auth.application.MfaService;
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
public class MfaController {

    private final MfaService mfaService;
    private final LoginService loginService;

    public MfaController(MfaService mfaService, LoginService loginService) {
        this.mfaService = mfaService;
        this.loginService = loginService;
    }

    @PostMapping("/setup")
    public MfaSetupResponse setup(Authentication authentication) {
        return MfaSetupResponse.of(mfaService.setup(currentUserId(authentication)));
    }

    @PostMapping("/enable")
    public MfaStatusResponse enable(Authentication authentication, @Valid @RequestBody MfaCodeRequest request) {
        mfaService.enable(currentUserId(authentication), request.code());
        return new MfaStatusResponse(true);
    }

    @PostMapping("/disable")
    public MfaStatusResponse disable(Authentication authentication, @Valid @RequestBody MfaCodeRequest request) {
        mfaService.disable(currentUserId(authentication), request.code());
        return new MfaStatusResponse(false);
    }

    @PostMapping("/login-verify")
    public TokenResponse loginVerify(@Valid @RequestBody MfaLoginVerifyRequest request) {
        return TokenResponse.of(loginService.verifyMfaAndLogin(request.mfaChallengeToken(), request.code()));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
