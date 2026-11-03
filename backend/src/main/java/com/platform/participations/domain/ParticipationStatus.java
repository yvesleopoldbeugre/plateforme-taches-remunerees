package com.platform.participations.domain;

/**
 * Mirrors the task_participations.status CHECK constraint (DAT §4.2). {@code IN_PROGRESS} is
 * allowed by the constraint but unused in the MVP flow — the sequence diagram goes straight
 * from RESERVED to SUBMITTED, so no endpoint transitions into it yet.
 */
public enum ParticipationStatus {
    RESERVED,
    IN_PROGRESS,
    SUBMITTED,
    UNDER_REVIEW,
    CORRECTION_REQUESTED,
    APPROVED,
    REJECTED,
    DISPUTED,
    CANCELLED,
    EXPIRED
}
