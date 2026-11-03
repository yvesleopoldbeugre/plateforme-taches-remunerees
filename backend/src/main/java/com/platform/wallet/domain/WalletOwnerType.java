package com.platform.wallet.domain;

/** Mirrors {@code wallets.owner_type} (DAT §4.2). A wallet is polymorphic: no FK to users/organizations. */
public enum WalletOwnerType {
    USER,
    ORGANIZATION
}
