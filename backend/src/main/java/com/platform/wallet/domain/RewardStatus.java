package com.platform.wallet.domain;

/** Mirrors the {@code rewards.status} CHECK constraint (DAT §4.2). */
public enum RewardStatus {
    PENDING,
    CREDITED,
    CANCELLED
}
