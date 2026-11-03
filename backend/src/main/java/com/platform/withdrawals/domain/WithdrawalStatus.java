package com.platform.withdrawals.domain;

/** Mirrors the {@code withdrawals.status} CHECK constraint (DAT §4.2). */
public enum WithdrawalStatus {
    REQUESTED,
    UNDER_REVIEW,
    APPROVED,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED,
    CANCELLED
}
