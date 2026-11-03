package com.platform.validation.domain;

/** Mirrors the validations.decision CHECK constraint (DAT §4.2). */
public enum ValidationDecision {
    AUTO_APPROVED,
    APPROVED,
    REJECTED,
    CORRECTION_REQUESTED
}
