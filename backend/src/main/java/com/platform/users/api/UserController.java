package com.platform.users.api;

import com.platform.users.api.dto.UpdateProfileRequest;
import com.platform.users.api.dto.UserMeResponse;
import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import com.platform.users.domain.UserProfile;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/v1/users/me")
    public UserMeResponse me(Authentication authentication) {
        UUID userId = currentUserId(authentication);
        User user = userService.getById(userId);
        UserProfile profile = userService.getProfile(userId);
        return UserMeResponse.of(user, profile);
    }

    @PatchMapping("/api/v1/users/me/profile")
    public UserMeResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = currentUserId(authentication);
        userService.updateProfile(userId, request.fullName(), request.country(), request.city(),
                request.birthDate(), request.language());
        User user = userService.getById(userId);
        UserProfile profile = userService.getProfile(userId);
        return UserMeResponse.of(user, profile);
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
