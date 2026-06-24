package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.binance.derivative.DerivativeExchangeAdapterFactory;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.TradeRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(BinanceConfig.Properties.class)
class BinanceConfig {


    @ConfigurationProperties(prefix = "binance")
    public record Properties(
            String apiKey,
            String secretKey,
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
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderHistoryRepository
    ) {
        return SpotExchangeAdapterFactory.create(
                properties,
                mainExecutor,
                ioExecutor,
                taskScheduler,
                objectMapper,
                webSocketClient,
                tradeRepository,
                orderHistoryRepository
        );
    }

    @Bean
    public DerivativeExchangeConnector binanceDerivativeExchangeConnector(
            Properties properties,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderHistoryRepository
    ) {
        return DerivativeExchangeAdapterFactory.create(
                properties,
                mainExecutor,
                ioExecutor,
                taskScheduler,
                objectMapper,
                webSocketClient,
                tradeRepository,
                orderHistoryRepository
        );

    }
}
