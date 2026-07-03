package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.binance.derivative.DerivativeExchangeAdapterFactory;
import com.hotak.noonchibot.connector.binance.spot.SpotExchangeAdapterFactory;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.derivative.FundingPaymentRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.strategy.safety.TradingSafetyController;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

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

    @Bean
    public ExchangeConnector binanceSpotExchangeConnector(
            Properties properties,
            BootStrap bootStrap,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderSnapshotRepository,
            TradingSafetyController tradingSafetyController
    ) {
        return SpotExchangeAdapterFactory.create(
                bootStrap,
                properties,
                orderSnapshotRepository,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                objectMapper,
                webSocketClient,
                tradeRepository,
                tradingSafetyController
        );
    }

    @Bean
    public DerivativeExchangeConnector binanceDerivativeExchangeConnector(
            Properties properties,
            BootStrap bootStrap,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            FundingPaymentRepository fundingPaymentRepository,
            OrderSnapshotRepository orderSnapshotRepository,
            TradingSafetyController tradingSafetyController
    ) {
        return DerivativeExchangeAdapterFactory.create(
                bootStrap,
                properties,
                orderSnapshotRepository,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                objectMapper,
                webSocketClient,
                tradeRepository,
                fundingPaymentRepository,
                tradingSafetyController
        );

    }
}
