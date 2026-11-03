package com.platform.fraud.domain;

/**
 * Mirrors {@code fraud_events.type} (DAT §4.2) — a plain-ish set, not an extensible allow-list
 * like {@code TaskTypes}, since each type is wired to its own detector, not user-facing config.
 * {@code MULTI_ACCOUNT_DEVICE} (BR-050) is defined but has no detector yet — see this phase's
 * README section for why (no device-fingerprinting capture point exists anywhere in the API).
 */
public enum FraudEventType {
    DUPLICATE_SUBMISSION,
    MULTI_ACCOUNT_DEVICE
}
