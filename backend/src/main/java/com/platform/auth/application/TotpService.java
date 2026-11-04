package com.platform.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30s step) — the same algorithm Google Authenticator/Authy/
 * 1Password implement, so any standard authenticator app works against secrets this issues. No
 * new Maven dependency: the whole algorithm is ~20 lines of {@code javax.crypto}, the same spirit
 * as the hand-rolled SHA-256 in {@code SubmissionService} (BR-051).
 *
 * <p>Stateless and side-effect-free — persistence of the secret is {@code UserService}'s job.
 */
@Service
public class TotpService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int ALLOWED_DRIFT_STEPS = 1; // ±30s clock drift tolerance either side
    private static final String ISSUER = "PlateformeTaches";

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[20]; // 160-bit, the conventional TOTP secret size
        random.nextBytes(bytes);
        return Base32Codec.encode(bytes);
    }

    public String provisioningUri(String secret, String accountEmail) {
        String label = urlEncode(ISSUER + ":" + accountEmail);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + urlEncode(ISSUER)
                + "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    /** The authenticator app computes this normally — exposed here mainly so tests can assert
     *  against a real, deterministic code instead of duplicating the algorithm. */
    public String currentCode(String secret) {
        return codeAt(secret, Instant.now().getEpochSecond() / TIME_STEP_SECONDS);
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (codeAt(secret, currentStep + drift).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String codeAt(String base32Secret, long step) {
        byte[] key = Base32Codec.decode(base32Secret);
        byte[] stepBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            stepBytes[i] = (byte) (step & 0xFF);
            step >>>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(stepBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA1 should always be available on the JVM", e);
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
