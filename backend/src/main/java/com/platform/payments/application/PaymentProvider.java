package com.platform.payments.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * H5 in the CDCF: "aucun provider n'est choisi en dur — le MVP inclut une abstraction
 * PaymentProvider avec un premier adaptateur manuel/simulé." Every real Mobile Money integration
 * (Orange Money, Wave, MTN...) implements this and is picked up automatically by
 * {@link PaymentProviderRegistry} — no change to {@code PaymentService}/{@code WithdrawalService}
 * needed to add one.
 *
 * <p>Deliberately takes primitive identifiers, not the {@code Payment}/{@code Withdrawal}
 * entities themselves: {@code payments} must not depend on {@code withdrawals} (which depends on
 * {@code payments} for {@code PaymentMethod} and this very registry) — see DAT §8.1.
 */
public interface PaymentProvider {

    String getCode();

    /** Organization deposit (cash-in) — {@code paymentId} is this operation's own id, for provider-side tracing. */
    PaymentProviderResult initiateDeposit(UUID paymentId, BigDecimal amount, String currency);

    /** User payout (cash-out) — {@code withdrawalId} is this operation's own id, {@code maskedIdentifier} the destination. */
    PaymentProviderResult initiatePayout(UUID withdrawalId, String maskedIdentifier, BigDecimal netAmount, String currency);
}
