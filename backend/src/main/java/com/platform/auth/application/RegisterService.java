package com.platform.auth.application;

import com.platform.users.application.UserService;
import com.platform.users.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates UC-01 (register + trigger verification) — BR-001. */
@Service
public class RegisterService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;

    public RegisterService(UserService userService, PasswordEncoder passwordEncoder,
                            VerificationService verificationService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.verificationService = verificationService;
    }

    @Transactional
    public User register(String email, String phone, String rawPassword, String fullName, String country) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = userService.registerUser(email, phone, passwordHash, fullName, country);
        verificationService.issueEmailVerification(user);
        verificationService.issuePhoneOtp(user);
        return user;
    }
}
