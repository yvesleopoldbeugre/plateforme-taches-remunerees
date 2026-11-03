package com.platform.users.api.dto;

import com.platform.users.domain.User;
import java.util.UUID;

/** Unlike {@link UserMeResponse}, doesn't require a {@code UserProfile} — admin status-change
 * actions (suspend/reactivate/ban, bootstrap) only need to echo the identity + account status. */
public record UserAdminResponse(
        UUID id, String email, String phone, String status, String platformRole
) {
    public static UserAdminResponse of(User user) {
        return new UserAdminResponse(user.getId(), user.getEmail(), user.getPhone(), user.getStatus().name(),
                user.getPlatformRole() == null ? null : user.getPlatformRole().name());
    }
}
