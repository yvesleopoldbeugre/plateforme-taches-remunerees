package com.platform.wallet.domain;

/** Mirrors {@code wallet_transactions.reference_type} (DAT §4.2) — what a ledger entry is about. */
public enum ReferenceType {
    REWARD,
    CAMPAIGN_BUDGET,
    DEPOSIT,
    WITHDRAWAL,
    PAYMENT,
    DISPUTE,
    ADMIN
}
