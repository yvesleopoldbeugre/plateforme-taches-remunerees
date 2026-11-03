package com.platform.wallet.domain;

/**
 * Mirrors {@code wallet_transactions.type} (DAT §4.2/§25). Each type moves a specific balance
 * bucket, enforced consistently between {@code WalletRepository}'s CAS updates and
 * {@code WalletService#reconstructBalances} (the Phase 7 ledger-invariant test relies on both
 * agreeing):
 *
 * <ul>
 *   <li>{@code CREDIT}, {@code DEBIT}, {@code FEE}, {@code REVERSAL}, {@code ADJUSTMENT} — move
 *       {@code available_balance} only; {@code amount} is the signed delta applied to it.</li>
 *   <li>{@code RESERVE}, {@code RELEASE} — move money between {@code available_balance} and
 *       {@code reserved_balance} within the same wallet (no change to their sum); {@code amount}
 *       is the signed delta applied to {@code available_balance} (negative for RESERVE, positive
 *       for RELEASE), and {@code reserved_balance} moves by exactly {@code -amount}.</li>
 *   <li>{@code WITHDRAWAL} — permanently consumes {@code reserved_balance} (money leaves the
 *       wallet system entirely: a reward paid out, or — from Phase 8 — a completed user
 *       withdrawal); {@code amount} is the signed delta applied to {@code reserved_balance}
 *       (always negative), {@code available_balance} is untouched.</li>
 * </ul>
 *
 * DEBIT/FEE/REVERSAL/ADJUSTMENT are defined now (the column is a Java enum, not an extensible
 * allow-list like {@code TaskTypes}) but have no service method yet — they belong to withdrawal
 * fees (Phase 8) and admin corrections (Phase 9).
 */
public enum TransactionType {
    CREDIT,
    DEBIT,
    RESERVE,
    RELEASE,
    WITHDRAWAL,
    FEE,
    REVERSAL,
    ADJUSTMENT
}
