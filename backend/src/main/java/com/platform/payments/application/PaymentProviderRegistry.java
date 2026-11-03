package com.platform.payments.application;

import com.platform.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Collects every {@link PaymentProvider} bean by its code — see {@link PaymentProvider} for why. */
@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> providersByCode;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providersByCode = providers.stream().collect(Collectors.toMap(PaymentProvider::getCode, Function.identity()));
    }

    public PaymentProvider get(String code) {
        PaymentProvider provider = providersByCode.get(code);
        if (provider == null) {
            throw new BusinessException("UNSUPPORTED_PAYMENT_PROVIDER", HttpStatus.BAD_REQUEST,
                    "Provider de paiement non supporté : " + code);
        }
        return provider;
    }
}
