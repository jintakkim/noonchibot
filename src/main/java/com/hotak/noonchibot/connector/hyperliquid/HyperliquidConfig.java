package com.hotak.noonchibot.connector.hyperliquid;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

class HyperliquidConfig {
    @ConfigurationProperties(prefix = "hyperliquid")
    public record Properties(
            String address,
            String secret,
            Derivative derivative
    ) {
        public record Derivative(
                Map<String, String> tradingPairSymbolMap
        ) {}
    }

}
