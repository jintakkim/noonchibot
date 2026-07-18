package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.core.strategy.risk.ExchangeEligibilityRiskGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExchangeFailureConfig {
    @Bean
    public ExchangeEligibilityRegistry exchangeEligibilityRegistry() {
        return new ExchangeEligibilityRegistry();
    }

    @Bean
    public ExchangeFailurePolicy exchangeFailurePolicy() {
        return new DefaultExchangeFailurePolicy();
    }

    @Bean
    public ExchangeEligibilityRiskGate exchangeEligibilityRiskGate(
            ExchangeEligibilityView exchangeEligibilityView
    ) {
        return new ExchangeEligibilityRiskGate(exchangeEligibilityView);
    }
}
