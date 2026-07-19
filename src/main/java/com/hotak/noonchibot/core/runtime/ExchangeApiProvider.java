package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;
public interface ExchangeApiProvider {
    ExchangeApi getExchange(Exchange exchange);

    default DerivativeExchangeApi getDerivativeExchange(Exchange exchange) {
        ExchangeApi api = getExchange(exchange);
        if (api instanceof DerivativeExchangeApi derivativeApi) {
            return derivativeApi;
        }
        throw new UnsupportedOperationException("Exchange is not a derivative exchange: " + exchange);
    }
}
