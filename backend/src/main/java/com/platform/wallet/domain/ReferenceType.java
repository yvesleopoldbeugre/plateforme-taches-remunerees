package com.platform.wallet.domain;

/**
 * Mirrors {@code wallet_transactions.reference_type} (DAT §4.2) — what a ledger entry is about.
 * {@code referenceId} points at that thing's own id: a {@code Campaign} for
 * {@code CAMPAIGN_BUDGET}, a {@code Reward}'s participation for {@code REWARD}, a {@code Payment}
 * for {@code PAYMENT} (Phase 8 — organization deposits), a {@code Withdrawal} for
 * {@code WITHDRAWAL} (Phase 8 — user payouts).
 */
public enum ReferenceType {
    REWARD,
    CAMPAIGN_BUDGET,
    WITHDRAWAL,
    PAYMENT,
    DISPUTE,
    ADMIN
}
