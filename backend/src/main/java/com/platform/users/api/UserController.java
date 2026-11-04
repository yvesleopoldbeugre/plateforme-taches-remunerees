package com.platform.users.api;

import com.platform.users.api.dto.UpdateProfileRequest;
import com.platform.users.api.dto.UserAdminResponse;
import com.platform.users.api.dto.UserMeResponse;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Utilisateurs", description = "Profil de l'utilisateur connecté et actions de "
        + "modération (suspension, réactivation, bannissement) réservées à PLATFORM_ADMIN (UC-21).")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Consulter mon profil")
    @GetMapping("/api/v1/users/me")
    public UserMeResponse me(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        User user = userService.getById(userId);
        UserProfile profile = userService.getProfile(userId);
        return UserMeResponse.of(user, profile);
    }

    @Operation(summary = "Mettre à jour mon profil")
    @PatchMapping("/api/v1/users/me/profile")
    public UserMeResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = currentUserId(authentication);
        userService.updateProfile(userId, request.fullName(), request.country(), request.city(),
                request.birthDate(), request.language());
        User user = userService.getById(userId);
        UserProfile profile = userService.getProfile(userId);
        return UserMeResponse.of(user, profile);
    }

    /** UC-21 — PLATFORM_ADMIN only, not OPERATOR: the CDCF is explicit that an operator "ne peut
     *  pas... suspendre une organisation sans escalade", and names only PLATFORM_ADMIN for UC-21. */
    @Operation(summary = "Suspendre un compte (admin)", description = "Révoque aussi immédiatement "
            + "toutes les sessions actives du compte (UC-21). Réservé à PLATFORM_ADMIN.")
    @PostMapping("/api/v1/users/{userId}/suspend")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public UserAdminResponse suspend(Authentication authentication, @PathVariable UUID userId) {
        return UserAdminResponse.of(userService.suspend(currentUserId(authentication), userId));
    }

    @Operation(summary = "Réactiver un compte suspendu ou banni (admin)")
    @PostMapping("/api/v1/users/{userId}/reactivate")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public UserAdminResponse reactivate(Authentication authentication, @PathVariable UUID userId) {
        return UserAdminResponse.of(userService.reactivate(currentUserId(authentication), userId));
    }

    @Operation(summary = "Bannir un compte (admin)")
    @PostMapping("/api/v1/users/{userId}/ban")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
    public UserAdminResponse ban(Authentication authentication, @PathVariable UUID userId) {
        return UserAdminResponse.of(userService.ban(currentUserId(authentication), userId));
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
