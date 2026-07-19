package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.FundingRateHistoryEventHandler;
import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.StructuredOrderIdGenerator;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceAuthenticator;
import com.hotak.noonchibot.connector.binance.BinanceConfig;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.connector.binance.BinancePriceCandleDataSource;
import com.hotak.noonchibot.connector.binance.BinanceServerTimeProvider;
import com.hotak.noonchibot.connector.binance.BinanceTradingRuleRegistry;
import com.hotak.noonchibot.connector.binance.DerivativeTradingRuleParser;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.web.MeteredRestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.TimestampRecoveringRestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.config.BotConstants;
import com.hotak.noonchibot.core.derivative.DerivativeAccountReconciler;
import com.hotak.noonchibot.core.derivative.DerivativeAccountTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.derivative.api.DerivativeModeCommandApi;
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
import com.hotak.noonchibot.core.runtime.DerivativeExchangeApi;
import com.hotak.noonchibot.core.runtime.ExchangeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@ExchangeConfiguration
public class BinanceDerivativeConfiguration {

    @Bean
    EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    RestClient restClient(BinanceConfig.Properties properties) {
        return RestClient.builder()
                .baseUrl(properties.network().isTestnet()
                        ? ApiSpec.TESTNET_REST_BASE_URL
                        : ApiSpec.REST_BASE_URL)
                .build();
    }

    @Bean
    TradingPairSymbolRegistry tradingPairSymbolRegistry(BinanceConfig.Properties properties) {
        return new SimpleTradingPairSymbolRegistry(properties.derivative().tradingPairSymbolMap());
    }

    @Bean
    AsyncThrottler asyncThrottler(IoExecutor ioExecutor) {
        return new AsyncThrottlerImpl(ApiSpec.RATE_LIMITS, ioExecutor);
    }

    @Bean
    TimeSynchronizer timeSynchronizer(
            RestClient restClient,
            AsyncThrottler throttler,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            TaskScheduler taskScheduler
    ) {
        RestAssistant serverTimeRestAssistant = new MeteredRestAssistant(
                new RestAssistantImpl(
                        restClient,
                        List.of(),
                        List.of(),
                        null,
                        throttler,
                        objectMapper,
                        new BinanceExchangeErrorClassifier(objectMapper)
                ),
                meterRegistry,
                Exchange.BINANCE_DERIVATIVE.name()
        );
        return new TimeSynchronizer(
                new BinanceServerTimeProvider(serverTimeRestAssistant, ApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
    }

    @Bean
    BinanceAuthenticator binanceAuthenticator(
            BinanceConfig.Properties properties,
            TimeSynchronizer timeSynchronizer,
            ObjectMapper objectMapper
    ) {
        return new BinanceAuthenticator(
                properties.apiKey(),
                properties.secretKey(),
                timeSynchronizer,
                objectMapper
        );
    }

    @Bean
    RestAssistant restAssistant(
            RestClient restClient,
            BinanceAuthenticator authenticator,
            AsyncThrottler throttler,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            TimeSynchronizer timeSynchronizer
    ) {
        RestAssistant authenticated = new MeteredRestAssistant(
                new RestAssistantImpl(
                        restClient,
                        List.of(),
                        List.of(),
                        authenticator,
                        throttler,
                        objectMapper,
                        new BinanceExchangeErrorClassifier(objectMapper)
                ),
                meterRegistry,
                Exchange.BINANCE_DERIVATIVE.name()
        );
        return new TimestampRecoveringRestAssistant(authenticated, timeSynchronizer, objectMapper);
    }

    @Bean
    WsAssistantImpl wsAssistant(
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            BinanceAuthenticator authenticator
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
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant,
            BinanceConfig.Properties properties,
            EventBus eventBus
    ) {
        return new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                symbols,
                restAssistant,
                properties.network().isTestnet()
                        ? ApiSpec.TESTNET_WSS_PUBLIC_URL
                        : ApiSpec.WSS_PUBLIC_URL,
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
                false,
                new HashSet<>(symbols.getAllTradingPairs())
        );
    }

    @Bean
    AccountBalanceTracker accountBalanceTracker(EventBus eventBus) {
        return new AccountBalanceTracker(eventBus);
    }

    @Bean
    TradeFeeSchemaLoader tradeFeeSchemaLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant
    ) {
        return new TradeFeeSchemaLoader(ioExecutor, symbols, restAssistant);
    }

    @Bean
    UserStreamDataSource userStreamDataSource(
            RestAssistant restAssistant,
            WsAssistantImpl wsAssistant,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            TradingPairSymbolRegistry symbols,
            EventBus eventBus,
            ApplicationEventPublisher applicationEventPublisher,
            BinanceConfig.Properties properties
    ) {
        return new UserStreamDataSource(
                restAssistant,
                wsAssistant,
                taskScheduler,
                objectMapper,
                symbols,
                eventBus,
                applicationEventPublisher,
                properties.network().isTestnet()
                        ? ApiSpec.TESTNET_WSS_PRIVATE_URL
                        : ApiSpec.WSS_PRIVATE_URL
        );
    }

    @Bean
    RestBalanceDataSource restBalanceDataSource(
            RestAssistant restAssistant,
            EventBus eventBus,
            TaskScheduler taskScheduler
    ) {
        return new RestBalanceDataSource(restAssistant, eventBus, taskScheduler);
    }

    @Bean
    BinanceTradingRuleRegistry tradingRuleRegistry(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper
    ) {
        return new BinanceTradingRuleRegistry(
                restAssistant,
                new DerivativeTradingRuleParser(symbols),
                taskScheduler,
                ApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                symbols,
                ApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper
        );
    }

    @Bean
    OrderClientImpl orderClient(
            TimeSynchronizer timeSynchronizer,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols
    ) {
        return new OrderClientImpl(timeSynchronizer, restAssistant, restAssistant, symbols);
    }

    @Bean
    ExchangeOrderExecutor exchangeOrderExecutor(
            OrderTracker orderTracker,
            BinanceTradingRuleRegistry tradingRuleRegistry,
            OrderBookTracker orderBookTracker,
            EventBus eventBus,
            OrderClientImpl orderClient,
            OrderSnapshotRepository orderSnapshotRepository
    ) {
        return new ExchangeOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                tradingRuleRegistry,
                BotConstants.ORDER_ID_PREFIX,
                ApiSpec.MAX_ORDER_ID_LENGTH,
                ApiSpec.SUPPORTED_TIME_IN_FORCE,
                orderBookTracker,
                eventBus,
                orderClient,
                eventBus,
                Exchange.BINANCE_DERIVATIVE,
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
    WsFundingInfoDataSource fundingInfoDataSource(
            WsAssistantImpl wsAssistant,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            EventBus eventBus,
            TradingPairSymbolRegistry symbols,
            BinanceConfig.Properties properties
    ) {
        return new WsFundingInfoDataSource(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                eventBus,
                symbols,
                properties.network().isTestnet()
                        ? ApiSpec.TESTNET_WSS_MARKET_URL
                        : ApiSpec.WSS_MARKET_URL
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
                Exchange.BINANCE_DERIVATIVE,
                ApiSpec.DEFAULT_FUNDING_INTERVAL,
                "USDT",
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
    FundingIntervalDataSource fundingIntervalDataSource(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols,
            EventBus eventBus
    ) {
        return new FundingIntervalDataSource(restAssistant, symbols, eventBus, eventBus);
    }

    @Bean
    FundingIntervalFetchScheduler fundingIntervalFetchScheduler(
            TaskScheduler taskScheduler,
            EventBus eventBus
    ) {
        return new FundingIntervalFetchScheduler(taskScheduler, eventBus);
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
    DerivativeAccountCommandExecutor derivativeAccountCommandExecutor(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols,
            EventBus eventBus
    ) {
        return new DerivativeAccountCommandExecutor(restAssistant, symbols, eventBus, eventBus);
    }

    @Bean
    DerivativeAccountReconciler derivativeAccountReconciler(
            DerivativeAccountTracker tracker,
            EventBus eventBus
    ) {
        return new DerivativeAccountReconciler(tracker, eventBus);
    }

    @Bean
    OrderStatusDataSource orderStatusDataSource(
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant,
            EventBus eventBus
    ) {
        return new OrderStatusDataSource(symbols, restAssistant, eventBus, eventBus);
    }

    @Bean
    TradeDataSource tradeDataSource(
            TradingPairSymbolRegistry symbols,
            RestAssistant restAssistant,
            EventBus eventBus
    ) {
        return new TradeDataSource(symbols, restAssistant, eventBus, eventBus);
    }

    @Bean
    OrderRecoveryBootstrap orderRecoveryBootstrap(
            OrderSnapshotRepository repository,
            OrderStatusDataSource orderStatusDataSource,
            TradeDataSource tradeDataSource,
            OrderTracker orderTracker
    ) {
        return new OrderRecoveryBootstrap(
                Exchange.BINANCE_DERIVATIVE,
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
            TradingPairSymbolRegistry symbols,
            TaskScheduler taskScheduler
    ) {
        return new OrderStatusPoller(eventBus, eventBus, orderTracker, symbols, taskScheduler);
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
    BinancePriceCandleDataSource priceCandleDataSource(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry symbols
    ) {
        return new BinancePriceCandleDataSource(
                Exchange.BINANCE_DERIVATIVE,
                restAssistant,
                symbols,
                ApiSpec.KLINE_PATH_URL
        );
    }

    @Bean
    DerivativeExchangeApi exchangeApi(
            ExchangeOrderExecutor orderCommand,
            OrderTracker orderQuery,
            DerivativeAccountReconciler derivativeModeCommand
    ) {
        return new DerivativeExchangeApi() {
            @Override
            public OrderCommandApi orderCommand() {
                return orderCommand;
            }

            @Override
            public OrderQueryApi orderQuery() {
                return orderQuery;
            }

            @Override
            public DerivativeModeCommandApi derivativeModeCommand() {
                return derivativeModeCommand;
            }
        };
    }
}
