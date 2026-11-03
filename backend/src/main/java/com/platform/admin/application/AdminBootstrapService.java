package com.platform.admin.application;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.ForbiddenException;
import com.platform.common.exception.NotFoundException;
import com.platform.users.application.UserService;
import com.platform.users.domain.PlatformRole;
import com.platform.users.domain.User;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the gap flagged as deferred since Phase 2: "bootstrap d'un premier compte
 * PLATFORM_ADMIN" — until now the only way to get one was a direct SQL update, done by hand for
 * every phase's manual verification.
 *
 * <p>{@code POST /api/v1/admin/bootstrap} is deliberately public (there is no admin yet to
 * authenticate as) but gated by a high-entropy shared secret read from
 * {@code platform.admin.bootstrap-secret} (env {@code ADMIN_BOOTSTRAP_SECRET}) and usable exactly
 * once — it refuses outright once any {@code PLATFORM_ADMIN} exists. The operator is expected to
 * set the secret, bootstrap the first admin, then rotate or unset it; this is a documented
 * operational step, not something the API can enforce on its own.
 */
@Service
public class AdminBootstrapService {

    private final UserService userService;
    private final String bootstrapSecret;

    public AdminBootstrapService(UserService userService,
                                  @Value("${platform.admin.bootstrap-secret:}") String bootstrapSecret) {
        this.userService = userService;
        this.bootstrapSecret = bootstrapSecret;
    }

    @Transactional
    public User bootstrap(String providedSecret, String email) {
        if (bootstrapSecret.isBlank()) {
            throw new ConflictException("BOOTSTRAP_DISABLED",
                    "Le bootstrap admin n'est pas activé (ADMIN_BOOTSTRAP_SECRET non configuré).");
        }
        if (!bootstrapSecret.equals(providedSecret)) {
            throw new ForbiddenException("INVALID_BOOTSTRAP_SECRET", "Secret de bootstrap invalide.");
        }
        if (userService.existsAnyPlatformAdmin()) {
            throw new ConflictException("ADMIN_ALREADY_EXISTS", "Un compte PLATFORM_ADMIN existe déjà.");
        }
        UUID userId = userService.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "Aucun compte utilisateur n'existe pour cet email — il doit d'abord s'inscrire normalement."))
                .getId();
        return userService.grantPlatformRole(userId, PlatformRole.PLATFORM_ADMIN);
    }
}
