package com.hotak.noonchibot.connector.upbit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(UpbitConfig.Properties.class)
class UpbitConfig {
    @ConfigurationProperties(prefix = "upbit")
    public record Properties(
            String apiKey,
            String secretKey,
            UpbitConfig.Properties.Spot spot
    ) {
        public record Spot(
                Map<String, String> tradingPairSymbolMap
        ) {}
    }
}
