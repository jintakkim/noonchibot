package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class SimpleExchangeApiProvider implements ExchangeApiProvider {
    private final ExchangeRuntimeManager runtimeManager;

    @Override
    public ExchangeApi getExchange(Exchange exchange) {
        return runtimeManager.getBean(exchange, ExchangeApi.class);
    }
}
