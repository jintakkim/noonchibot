package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.DerivativeExchangeConnector;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.derivative.FundingPaymentRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.TradeRepository;
import org.msgpack.jackson.dataformat.MessagePackMapper;
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
@EnableConfigurationProperties(HyperliquidConfig.Properties.class)
public class HyperliquidConfig {
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

    @Bean
    public DerivativeExchangeConnector hyperliquidDerivativeExchangeConnector(
            Properties properties,
            BootStrap bootStrap,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            MessagePackMapper messagePackMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            FundingPaymentRepository fundingPaymentRepository,
            OrderSnapshotRepository orderHistoryRepository
    ) {
        return DerivativeExchangeAdapterFactory.create(
                bootStrap,
                properties,
                orderHistoryRepository,
                ioExecutor,
                taskScheduler,
                objectMapper,
                messagePackMapper,
                webSocketClient,
                tradeRepository,
                fundingPaymentRepository,
                orderHistoryRepository
        );
    }
}
