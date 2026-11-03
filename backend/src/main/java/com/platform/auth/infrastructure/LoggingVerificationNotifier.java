package com.platform.auth.infrastructure;

import com.platform.auth.application.VerificationNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dev-stage stand-in for the real email/SMS providers. Logs instead of sending, so the
 * register → verify flow is testable end-to-end before the notifications module exists.
 */
@Component
public class LoggingVerificationNotifier implements VerificationNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerificationNotifier.class);

    @Override
    public void sendEmailVerification(String email, String token) {
        log.info("[DEV] Email verification link for {} -> token={}", email, token);
    }

    @Override
    public void sendPhoneOtp(String phone, String code) {
        log.info("[DEV] SMS OTP for {} -> code={}", phone, code);
    }
}
