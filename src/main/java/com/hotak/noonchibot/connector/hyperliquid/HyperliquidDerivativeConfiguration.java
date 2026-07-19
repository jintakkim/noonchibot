package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.FundingRateHistoryEventHandler;
import com.hotak.noonchibot.connector.SimpleExchangeErrorClassifier;
import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.transfer.TransferDispatcher;
import com.hotak.noonchibot.connector.web.MeteredRestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.config.BotConstants;
import com.hotak.noonchibot.core.derivative.DerivativeAccountTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.derivative.funding.FundingHistoryProperties;
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentRepository;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentSnapshotUpdater;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentTracker;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderRecoveryBootstrap;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotUpdater;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.order.api.OrderCommandApi;
import com.hotak.noonchibot.core.order.api.OrderQueryApi;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.runtime.ExchangeApi;
import com.hotak.noonchibot.core.runtime.ExchangeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

@ExchangeConfiguration
public class HyperliquidDerivativeConfiguration {

    @Bean
    EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    RestClient restClient(HyperliquidConfig.Properties properties) {
        return RestClient.builder()
                .baseUrl(properties.network().isTestnet()
                        ? DerivativeApiSpec.TESTNET_BASE_URL
                        : DerivativeApiSpec.BASE_URL)
                .build();
    }

    @Bean
    TradingPairSymbolRegistry tradingPairSymbolRegistry(HyperliquidConfig.Properties properties) {
        return new SimpleTradingPairSymbolRegistry(properties.derivative().tradingPairSymbolMap());
    }

    @Bean
    AsyncThrottler asyncThrottler(IoExecutor ioExecutor) {
        return new AsyncThrottlerImpl(DerivativeApiSpec.RATE_LIMITS, ioExecutor);
    }

    @Bean
    HyperliquidAuthenticator hyperliquidAuthenticator(
            HyperliquidConfig.Properties properties,
            ObjectMapper objectMapper,
            MessagePackMapper messagePackMapper
    ) {
        return new HyperliquidAuthenticator(
                objectMapper,
                messagePackMapper,
                null,
                !properties.network().isTestnet(),
                properties.address(),
                properties.secret()
        );
    }

    @Bean
    RestAssistant restAssistant(
            RestClient restClient,
            HyperliquidAuthenticator authenticator,
            AsyncThrottler throttler,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        return new MeteredRestAssistant(
                new RestAssistantImpl(
                        restClient,
                        List.of(new HyperliquidRateLimitPreProcessor()),
                        List.of(),
                        authenticator,
                        throttler,
                        objectMapper,
                        new SimpleExchangeErrorClassifier()
                ),
                meterRegistry,
                Exchange.HYPERLIQUID_DERIVATIVE.name()
        );
    }

    @Bean
    WsAssistantImpl wsAssistant(
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            HyperliquidAuthenticator authenticator
    ) {
        return new WsAssistantImpl(
                webSocketClient,
                new WebSocketHttpHeaders(),
                List.of(),
                List.of(),
                objectMapper,
                authenticator
        );
    }

    @Bean
    FundingRateHistoryDataSourceImpl fundingRateHistoryDataSource(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols
    ) {
        return new FundingRateHistoryDataSourceImpl(restAssistant, symbols);
    }

    @Bean
    FundingRateHistoryEventHandler fundingRateHistoryEventHandler(
            FundingRateHistoryDataSourceImpl dataSource,
            EventBus eventBus
    ) {
        return new FundingRateHistoryEventHandler(dataSource, eventBus, eventBus);
    }

    @Bean
    OrderBookDataSource orderBookDataSource(
            WsAssistantImpl wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols,
            HyperliquidConfig.Properties properties,
            EventBus eventBus
    ) {
        return new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                restAssistant,
                symbols,
                websocketUrl(properties),
                eventBus,
                eventBus
        );
    }

    @Bean
    OrderTracker orderTracker(
            EventBus eventBus,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderSnapshotRepository
    ) {
        return new OrderTracker(eventBus, tradeRepository, orderSnapshotRepository, eventBus);
    }

    @Bean
    OrderBookTracker orderBookTracker(EventBus eventBus, TradingPairSymbolRegistry symbols) {
        return new OrderBookTracker(
                eventBus,
                eventBus,
                true,
                new HashSet<>(symbols.getAllTradingPairs())
        );
    }

    @Bean
    AccountBalanceTracker accountBalanceTracker(EventBus eventBus) {
        return new AccountBalanceTracker(eventBus);
    }

    @Bean
    DerivativeTradeFeeSchemaLoader tradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant,
            HyperliquidConfig.Properties properties
    ) {
        return new DerivativeTradeFeeSchemaLoader(
                ioExecutor,
                symbols,
                restAssistant,
                properties.address()
        );
    }

    @Bean
    UserStreamDataSource userStreamDataSource(
            WsAssistantImpl wsAssistant,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            HyperliquidConfig.Properties properties,
            TradingPairSymbolRegistry symbols,
            EventBus eventBus
    ) {
        return new UserStreamDataSource(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                properties.address(),
                symbols,
                eventBus,
                websocketUrl(properties)
        );
    }

    @Bean
    RestBalanceDataSource restBalanceDataSource(
            RestAssistant restAssistant,
            EventBus eventBus,
            TaskScheduler taskScheduler,
            HyperliquidConfig.Properties properties
    ) {
        return new RestBalanceDataSource(
                restAssistant,
                eventBus,
                taskScheduler,
                properties.address()
        );
    }

    @Bean
    HLTradingRuleRegistry tradingRuleRegistry(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols,
            TaskScheduler taskScheduler
    ) {
        return new HLTradingRuleRegistry(restAssistant, symbols, taskScheduler);
    }

    @Bean
    OrderClientImpl orderClient(RestAssistant restAssistant, HLTradingRuleRegistry tradingRules) {
        return new OrderClientImpl(restAssistant, restAssistant, tradingRules);
    }

    @Bean
    ExchangeOrderExecutor exchangeOrderExecutor(
            OrderTracker orderTracker,
            HLTradingRuleRegistry tradingRules,
            OrderBookTracker orderBookTracker,
            EventBus eventBus,
            OrderClientImpl orderClient,
            OrderSnapshotRepository orderSnapshotRepository
    ) {
        return new ExchangeOrderExecutor(
                new HyperliquidCloidGenerator(),
                orderTracker,
                tradingRules,
                BotConstants.ORDER_ID_PREFIX,
                DerivativeApiSpec.MAX_ORDER_ID_LENGTH,
                DerivativeApiSpec.SUPPORTED_TIME_IN_FORCE,
                orderBookTracker,
                eventBus,
                orderClient,
                eventBus,
                Exchange.HYPERLIQUID_DERIVATIVE,
                orderSnapshotRepository
        );
    }

    @Bean
    OrderSnapshotUpdater orderSnapshotUpdater(
            OrderSnapshotRepository repository,
            EventBus eventBus
    ) {
        return new OrderSnapshotUpdater(repository, eventBus);
    }

    @Bean
    OrderStatusDataSource orderStatusDataSource(
            RestAssistant restAssistant,
            EventBus eventBus,
            HyperliquidConfig.Properties properties
    ) {
        return new OrderStatusDataSource(restAssistant, eventBus, eventBus, properties.address());
    }

    @Bean
    TradeDataSource tradeDataSource(
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant,
            EventBus eventBus,
            HyperliquidConfig.Properties properties
    ) {
        return new TradeDataSource(
                symbols,
                restAssistant,
                eventBus,
                eventBus,
                properties.address()
        );
    }

    @Bean
    OrderRecoveryBootstrap orderRecoveryBootstrap(
            OrderSnapshotRepository repository,
            OrderStatusDataSource orderStatusDataSource,
            TradeDataSource tradeDataSource,
            OrderTracker orderTracker
    ) {
        return new OrderRecoveryBootstrap(
                Exchange.HYPERLIQUID_DERIVATIVE,
                repository,
                orderStatusDataSource,
                tradeDataSource,
                orderTracker
        );
    }

    @Bean
    OrderStatusPoller orderStatusPoller(
            EventBus eventBus,
            OrderTracker orderTracker,
            TaskScheduler taskScheduler
    ) {
        return new OrderStatusPoller(eventBus, eventBus, orderTracker, taskScheduler);
    }

    @Bean
    TradePoller tradePoller(
            EventBus eventBus,
            TaskScheduler taskScheduler,
            OrderTracker orderTracker
    ) {
        return new TradePoller(eventBus, eventBus, taskScheduler, orderTracker);
    }

    @Bean
    DeriviativeInnerTransfer derivativeInnerTransfer(RestAssistant restAssistant) {
        return new DeriviativeInnerTransfer(restAssistant);
    }

    @Bean
    TransferDispatcher transferDispatcher(
            DeriviativeInnerTransfer innerTransfer,
            EventBus eventBus
    ) {
        return new TransferDispatcher(List.of(innerTransfer), eventBus, eventBus);
    }

    @Bean
    HyperliquidWsFundingInfoDataSource fundingInfoDataSource(
            WsAssistantImpl wsAssistant,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant,
            EventBus eventBus,
            HyperliquidConfig.Properties properties
    ) {
        return new HyperliquidWsFundingInfoDataSource(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                symbols,
                restAssistant,
                eventBus,
                symbols.getAllTradingPairs(),
                websocketUrl(properties)
        );
    }

    @Bean
    FundingInfoTracker fundingInfoTracker(
            TradingPairSymbolRegistry symbols,
            EventBus eventBus,
            ApplicationEventPublisher applicationEventPublisher,
            FundingRateHistoryDataSourceImpl fundingRateHistoryDataSource,
            TaskScheduler taskScheduler,
            FundingHistoryProperties properties
    ) {
        return new FundingInfoTracker(
                Exchange.HYPERLIQUID_DERIVATIVE,
                Duration.ofHours(1),
                "USDC",
                symbols,
                eventBus,
                eventBus,
                applicationEventPublisher,
                fundingRateHistoryDataSource,
                taskScheduler,
                properties
        );
    }

    @Bean
    DerivativeAccountTracker derivativeAccountTracker(EventBus eventBus) {
        return new DerivativeAccountTracker(eventBus);
    }

    @Bean
    PositionTracker positionTracker(EventBus eventBus) {
        return new PositionTracker(eventBus);
    }

    @Bean
    FundingPaymentTracker fundingPaymentTracker(PositionTracker positionTracker, EventBus eventBus) {
        return new FundingPaymentTracker(positionTracker, eventBus, eventBus);
    }

    @Bean
    FundingPaymentSnapshotUpdater fundingPaymentSnapshotUpdater(
            FundingPaymentRepository repository,
            EventBus eventBus
    ) {
        return new FundingPaymentSnapshotUpdater(repository, eventBus);
    }

    @Bean
    HyperliquidPriceCandleDataSource priceCandleDataSource(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols
    ) {
        return new HyperliquidPriceCandleDataSource(restAssistant, symbols);
    }

    @Bean
    ExchangeApi exchangeApi(ExchangeOrderExecutor orderCommand, OrderTracker orderQuery) {
        return new ExchangeApi() {
            @Override
            public OrderCommandApi orderCommand() {
                return orderCommand;
            }

            @Override
            public OrderQueryApi orderQuery() {
                return orderQuery;
            }
        };
    }

    private String websocketUrl(HyperliquidConfig.Properties properties) {
        return properties.network().isTestnet()
                ? DerivativeApiSpec.TESTNET_WS_URL
                : DerivativeApiSpec.WS_URL;
    }
}
