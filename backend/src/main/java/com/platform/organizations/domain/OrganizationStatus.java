package com.platform.organizations.domain;

/** Mirrors the organizations.status CHECK constraint (DAT §4.2). */
public enum OrganizationStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED
}
