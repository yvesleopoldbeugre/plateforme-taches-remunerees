package com.platform.users.application;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.NotFoundException;
import com.platform.users.domain.User;
import com.platform.users.domain.UserProfile;
import com.platform.users.infrastructure.UserProfileRepository;
import com.platform.users.infrastructure.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service exposed to other modules (notably {@code auth}) — see the dependency
 * rule in DAT §8.2: other modules read/write users only through this service, never through
 * {@link UserRepository} directly.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserService(UserRepository userRepository, UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional
    public User registerUser(String email, String phone, String passwordHash, String fullName, String country) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_USED", "Cette adresse email est déjà utilisée.");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new ConflictException("PHONE_ALREADY_USED", "Ce numéro de téléphone est déjà utilisé.");
        }
        User user = userRepository.save(new User(email, phone, passwordHash));
        userProfileRepository.save(new UserProfile(user.getId(), fullName, country));
        return user;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User getById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Utilisateur introuvable."));
    }

    public UserProfile getProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Profil introuvable."));
    }

    @Transactional
    public User markEmailVerified(UUID userId) {
        User user = getById(userId);
        user.markEmailVerified();
        return user;
    }

    @Transactional
    public User markPhoneVerified(UUID userId) {
        User user = getById(userId);
        user.markPhoneVerified();
        return user;
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, String fullName, String country, String city,
                                      LocalDate birthDate, String language) {
        UserProfile profile = getProfile(userId);
        profile.updateProfile(fullName, country, city, birthDate, language);
        return profile;
    }
}
