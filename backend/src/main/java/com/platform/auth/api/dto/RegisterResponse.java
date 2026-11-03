package com.platform.auth.api.dto;

import com.platform.users.domain.User;
import java.util.UUID;

public record RegisterResponse(UUID userId, String status) {
    public static RegisterResponse of(User user) {
        return new RegisterResponse(user.getId(), user.getStatus().name());
    }
}
