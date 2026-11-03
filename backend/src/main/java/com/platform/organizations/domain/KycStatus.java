package com.platform.organizations.domain;

/** Mirrors the organizations.kyc_status CHECK constraint added in V3 (DAT §4.2, hypothesis H6). */
public enum KycStatus {
    NOT_SUBMITTED,
    PENDING_REVIEW,
    VERIFIED,
    REJECTED
}
