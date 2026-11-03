package com.platform.users.application;

import com.platform.common.exception.ConflictException;
import com.platform.common.exception.NotFoundException;
import com.platform.users.domain.PlatformRole;
import com.platform.users.domain.User;
import com.platform.users.domain.UserProfile;
import com.platform.users.domain.UserStatus;
import com.platform.users.domain.event.UserStatusChangedEvent;
import com.platform.users.infrastructure.UserProfileRepository;
import com.platform.users.infrastructure.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, UserProfileRepository userProfileRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.eventPublisher = eventPublisher;
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

    @Transactional
    public User suspend(UUID actorId, UUID userId) {
        return changeStatus(actorId, userId, User::suspend);
    }

    @Transactional
    public User reactivate(UUID actorId, UUID userId) {
        return changeStatus(actorId, userId, User::reactivate);
    }

    @Transactional
    public User ban(UUID actorId, UUID userId) {
        return changeStatus(actorId, userId, User::ban);
    }

    private User changeStatus(UUID actorId, UUID userId, java.util.function.Consumer<User> transition) {
        User user = getById(userId);
        UserStatus previous = user.getStatus();
        transition.accept(user);
        eventPublisher.publishEvent(new UserStatusChangedEvent(actorId, userId, previous, user.getStatus()));
        return user;
    }

    public boolean existsAnyPlatformAdmin() {
        return userRepository.existsByPlatformRole(PlatformRole.PLATFORM_ADMIN);
    }

    /** Phase 11 — platform KPI ("utilisateurs actifs"). */
    public long countByStatus(UserStatus status) {
        return userRepository.countByStatus(status);
    }

    /** Admin bootstrap only (see {@code AdminBootstrapService}) — not exposed as a general-purpose
     *  role grant, since there is no admin-facing "manage roles" screen in this MVP. */
    @Transactional
    public User grantPlatformRole(UUID userId, PlatformRole role) {
        User user = getById(userId);
        user.grantPlatformRole(role);
        return user;
    }
}
