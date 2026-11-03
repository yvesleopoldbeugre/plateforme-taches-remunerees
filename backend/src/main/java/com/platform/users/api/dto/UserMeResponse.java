package com.platform.users.api.dto;

import com.platform.users.domain.User;
import com.platform.users.domain.UserProfile;
import java.time.LocalDate;
import java.util.UUID;

public record UserMeResponse(
        UUID id,
        String email,
        String phone,
        String status,
        boolean emailVerified,
        boolean phoneVerified,
        String fullName,
        String country,
        String city,
        LocalDate birthDate,
        String language
) {
    public static UserMeResponse of(User user, UserProfile profile) {
        return new UserMeResponse(
                user.getId(), user.getEmail(), user.getPhone(), user.getStatus().name(),
                user.isEmailVerified(), user.isPhoneVerified(),
                profile.getFullName(), profile.getCountry(), profile.getCity(),
                profile.getBirthDate(), profile.getLanguage());
    }
}
