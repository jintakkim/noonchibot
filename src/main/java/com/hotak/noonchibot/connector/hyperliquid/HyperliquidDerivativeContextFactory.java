package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.runtime.ExchangeContextBuilder;
import com.hotak.noonchibot.core.runtime.ExchangeContextFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public final class HyperliquidDerivativeContextFactory implements ExchangeContextFactory {
    private final ExchangeContextBuilder contextBuilder;

    @Override
    public Exchange exchange() {
        return Exchange.HYPERLIQUID_DERIVATIVE;
    }

    @Override
    public ConfigurableApplicationContext buildContext() {
        return contextBuilder.build(exchange(), HyperliquidDerivativeConfiguration.class);
    }
}
