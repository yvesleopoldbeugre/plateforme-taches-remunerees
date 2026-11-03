package com.platform.fraud.domain;

/**
 * Mirrors the {@code fraud_events.status} CHECK constraint (DAT §4.2). Matches the CDCF's own
 * phrasing for UC-22's three outcomes: {@code REVIEWED} ("escalade" — looked at, not yet a final
 * call), {@code CONFIRMED} ("suspension de compte"), {@code DISMISSED} ("classement sans suite").
 */
public enum FraudEventStatus {
    OPEN,
    REVIEWED,
    CONFIRMED,
    DISMISSED
}
