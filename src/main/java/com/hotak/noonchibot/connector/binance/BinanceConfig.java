package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.Network;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(BinanceConfig.Properties.class)
public class BinanceConfig {

    @ConfigurationProperties(prefix = "binance")
    public record Properties(
            String apiKey,
            String secretKey,
            Network network,
            Spot spot,
            Derivative derivative
    ) {
        public record Spot(
                Map<String, String> tradingPairSymbolMap
        ) {}
        public record Derivative(
                Map<String, String> tradingPairSymbolMap
        ) {}
    }

}
