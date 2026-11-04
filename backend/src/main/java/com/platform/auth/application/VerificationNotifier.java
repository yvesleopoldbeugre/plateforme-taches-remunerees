package com.platform.auth.application;

/**
 * Port for delivering verification messages. Until the {@code notifications} module (Phase 9
 * of the roadmap) provides real email/SMS adapters, {@code LoggingVerificationNotifier} is the
 * only implementation — see DAT §7.2 (provider abstraction principle).
 */
public interface VerificationNotifier {

    void sendEmailVerification(String email, String token);

    void sendPhoneOtp(String phone, String code);

    /** Phase 12 — password reset request (CDCT §8). */
    void sendPasswordResetEmail(String email, String token);
}
