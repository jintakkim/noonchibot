package com.hotak.noonchibot.core.strategy.safety;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TradingSafetyConfig {
    @Bean
    public TradingSafetyController tradingSafetyController() {
        return new TradingSafetyController();
    }
}
