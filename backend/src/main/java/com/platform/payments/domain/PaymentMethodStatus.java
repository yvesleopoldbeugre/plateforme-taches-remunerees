package com.platform.payments.domain;

/** Mirrors {@code payment_methods.status} (DAT §4.2) — no CHECK constraint in the DDL, but
 * typed here for the same reason every other status column in this codebase is an enum. */
public enum PaymentMethodStatus {
    ACTIVE,
    INACTIVE
}
