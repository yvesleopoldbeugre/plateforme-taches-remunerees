package com.platform.payments.application;

/** Outcome of a {@link PaymentProvider} call — deliberately just success/reference/reason, no
 * entity reference, so this type stays usable from both {@code payments} and {@code withdrawals}. */
public record PaymentProviderResult(boolean succeeded, String providerReference, String failureReason) {

    public static PaymentProviderResult success(String providerReference) {
        return new PaymentProviderResult(true, providerReference, null);
    }

    public static PaymentProviderResult failure(String reason) {
        return new PaymentProviderResult(false, null, reason);
    }
}
