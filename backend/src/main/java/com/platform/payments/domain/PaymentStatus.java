package com.platform.payments.domain;

/** Mirrors the {@code payments.status} CHECK constraint (DAT §4.2). */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}
