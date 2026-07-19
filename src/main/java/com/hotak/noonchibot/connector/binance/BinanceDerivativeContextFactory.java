package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.binance.derivative.BinanceDerivativeConfiguration;
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
public final class BinanceDerivativeContextFactory implements ExchangeContextFactory {
    private final ExchangeContextBuilder contextBuilder;

    @Override
    public Exchange exchange() {
        return Exchange.BINANCE_DERIVATIVE;
    }

    @Override
    public ConfigurableApplicationContext buildContext() {
        return contextBuilder.build(exchange(), BinanceDerivativeConfiguration.class);
    }
}
