package com.platform.campaigns.domain;

/**
 * Mirrors the campaigns.status CHECK constraint (DAT §4.2) and the corrected state machine
 * from the design dossier (DAT §10.1): REJECTED and the FULL→PUBLISHED transition were added
 * on top of the originally proposed machine.
 */
public enum CampaignStatus {
    DRAFT,
    PENDING_REVIEW,
    REJECTED,
    PUBLISHED,
    PAUSED,
    FULL,
    COMPLETED,
    EXPIRED,
    CANCELLED
}
