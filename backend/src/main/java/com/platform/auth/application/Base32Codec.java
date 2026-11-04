package com.platform.auth.application;

import java.io.ByteArrayOutputStream;

/**
 * Minimal RFC 4648 §6 Base32 (no padding) — {@code java.util.Base64} has no Base32 counterpart,
 * and TOTP secrets/provisioning URIs are conventionally Base32 (RFC 6238 / Google Authenticator
 * convention). Package-private: only {@link TotpService} needs this.
 */
final class Base32Codec {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32Codec() {
    }

    static String encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int bits = 0;
        int value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                result.append(ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            result.append(ALPHABET.charAt((value << (5 - bits)) & 0x1F));
        }
        return result.toString();
    }

    static byte[] decode(String encoded) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bits = 0;
        int value = 0;
        for (char c : encoded.toUpperCase().toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index < 0) {
                continue; // skip padding/whitespace, if any ever sneaks in
            }
            value = (value << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
