package com.platform.payments.domain;

import java.util.Set;

/**
 * Plain string allow-list, not a Java enum — same reasoning as {@code TaskTypes} (CDCF §2.1):
 * {@code payment_methods.type} is a VARCHAR, and the MVP ships exactly the two families the
 * design dossier names (Mobile Money dominant in the target market, bank transfer as the
 * secondary option), with room to add more without a migration.
 */
public final class PaymentMethodTypes {

    public static final String MOBILE_MONEY = "MOBILE_MONEY";
    public static final String BANK_ACCOUNT = "BANK_ACCOUNT";

    public static final Set<String> SUPPORTED = Set.of(MOBILE_MONEY, BANK_ACCOUNT);

    private PaymentMethodTypes() {
    }
}
