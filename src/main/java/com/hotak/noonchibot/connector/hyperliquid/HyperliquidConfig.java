package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.Network;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(HyperliquidConfig.Properties.class)
public class HyperliquidConfig {
    @ConfigurationProperties(prefix = "hyperliquid")
    public record Properties(
            String address,
            String secret,
            Network network,
            Derivative derivative
    ) {
        public record Derivative(
                Map<String, String> tradingPairSymbolMap
        ) {}
    }
}
