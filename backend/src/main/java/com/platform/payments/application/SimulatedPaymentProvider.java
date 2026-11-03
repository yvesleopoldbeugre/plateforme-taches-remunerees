package com.platform.payments.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The "mode simulé" adapter from H5 (CDCF) — stands in until a real Mobile Money provider is
 * integrated. Always succeeds, synchronously: there is no real network round-trip to model, so
 * the webhook-callback path a real async provider would need is intentionally not built here —
 * {@code PaymentProvider} is shaped so that adding it later (a real provider returning "pending"
 * and confirming via webhook) doesn't require reshaping {@code PaymentService}/
 * {@code WithdrawalService}, but building that path now, for a provider that doesn't exist yet,
 * would be speculative.
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    public static final String CODE = "SIMULATED";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public PaymentProviderResult initiateDeposit(UUID paymentId, BigDecimal amount, String currency) {
        return PaymentProviderResult.success("SIM-DEP-" + paymentId);
    }

    @Override
    public PaymentProviderResult initiatePayout(UUID withdrawalId, String maskedIdentifier, BigDecimal netAmount, String currency) {
        return PaymentProviderResult.success("SIM-PAYOUT-" + withdrawalId);
    }
}
