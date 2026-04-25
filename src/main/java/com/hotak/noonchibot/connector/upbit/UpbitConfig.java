package com.hotak.noonchibot.connector.upbit;

import com.hotak.noonchibot.connector.ExchangeAdapter;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.order.OrderHistoryRepository;
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

    @Bean
    public ExchangeAdapter upbitSpotExchangeAdapter(
            UpbitConfig.Properties properties,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository
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
}
