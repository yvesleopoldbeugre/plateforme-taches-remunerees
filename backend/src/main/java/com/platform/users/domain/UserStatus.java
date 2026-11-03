package com.platform.users.domain;

/** Mirrors the users.status CHECK constraint (DAT §4.2). */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    BANNED
}
