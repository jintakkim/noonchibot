package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.binance.*;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.throttle.ThrottlerLimitIdPreProcessor;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.config.BotConstants;
import com.hotak.noonchibot.core.derivative.DerivativeInfoTracker;
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.funding.FundingHistoryProperties;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentRepository;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentSnapshotUpdater;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.exchange.ExchangeEligibilityRegistry;
import com.hotak.noonchibot.core.exchange.ExchangeFailureCoordinator;
import com.hotak.noonchibot.core.exchange.ExchangeFailurePolicy;
import com.hotak.noonchibot.core.exchange.ExchangeRecoveryProbe;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderReconciliationCoordinator;
import com.hotak.noonchibot.core.order.OrderReconciliationTaskRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotUpdater;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderRecoveryBootstrap;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;

public class DerivativeExchangeAdapterFactory {
    public static DerivativeExchangeConnector create(
            BootStrap bootStrap,
            BinanceConfig.Properties props,
            OrderSnapshotRepository orderSnapshotRepository,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            FundingPaymentRepository fundingPaymentRepository,
            FundingHistoryProperties fundingHistoryProperties,
            OrderReconciliationTaskRepository reconciliationTaskRepository,
            ExchangeFailurePolicy exchangeFailurePolicy,
            ExchangeEligibilityRegistry exchangeEligibilityRegistry
    ) {
        EventBus eventBus = new EventBus();
        boolean testnet = props.network().isTestnet();
        RestClient restClient = RestClient.builder()
                .baseUrl(testnet ? ApiSpec.TESTNET_REST_BASE_URL : ApiSpec.REST_BASE_URL)
                .build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.derivative().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(ApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BinanceServerTimeProvider(
                        rest(
                                new RestAssistantImpl(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                                2,
                                objectMapper
                        ),
                        ApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
        bootStrap.register(timeSynchronizer);

        BinanceAuthenticator authenticator = new BinanceAuthenticator(props.apiKey(), props.secretKey(), timeSynchronizer, objectMapper);
        RestAssistant rawRestAssistant = new RestAssistantImpl(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor()),
                List.of(),
                authenticator,
                throttler,
                objectMapper
        );
        RestAssistant baseRestAssistant = new BinanceTimestampRecoveringRestAssistant(
                rawRestAssistant,
                timeSynchronizer,
                objectMapper
        );
        FundingRateHistoryDataSourceImpl fundingRateHistoryDataSource =
                new FundingRateHistoryDataSourceImpl(
                        rest(baseRestAssistant, 2, objectMapper),
                        tradingPairSymbolRegistry
                );
        bootStrap.register(new FundingRateHistoryEventHandler(fundingRateHistoryDataSource, eventBus, eventBus));
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);
        OrderBookDataSource orderBookDataSource = new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, 2, objectMapper),
                testnet ? ApiSpec.TESTNET_WSS_PUBLIC_URL : ApiSpec.WSS_PUBLIC_URL,
                eventBus,
                eventBus
        );
        bootStrap.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, tradeRepository, orderSnapshotRepository, eventBus);
        bootStrap.register(orderTracker);

        OrderBookTracker orderBookTracker = new OrderBookTracker(
                eventBus,
                eventBus,
                false,
                new HashSet<>(tradingPairSymbolRegistry.getAllTradingPairs())
        );
        bootStrap.register(orderBookTracker);

        AccountBalanceTracker accountBalanceTracker = new AccountBalanceTracker(eventBus);
        bootStrap.register(accountBalanceTracker);

        TradeFeeSchemaLoader feeSchemaLoader = new TradeFeeSchemaLoader(
                ioExecutor,
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, 2, objectMapper)
        );
        bootStrap.register(feeSchemaLoader);

        UserStreamDataSource userStreamDataSource = new UserStreamDataSource(
                rest(baseRestAssistant, 2, objectMapper),
                wsAssistant,
                taskScheduler,
                objectMapper,
                tradingPairSymbolRegistry,
                eventBus,
                applicationEventPublisher,
                testnet ? ApiSpec.TESTNET_WSS_PRIVATE_URL : ApiSpec.WSS_PRIVATE_URL
        );
        bootStrap.register(userStreamDataSource);

        RestBalanceDataSource balancePoller = new RestBalanceDataSource(
                rest(baseRestAssistant, 2, objectMapper),
                eventBus,
                taskScheduler
        );
        bootStrap.register(balancePoller);

        BinanceTradingRuleRegistry binanceTradingRuleRegistry = new BinanceTradingRuleRegistry(
                rest(baseRestAssistant, 2, objectMapper),
                new DerivativeTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                ApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                ApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper
        );
        bootStrap.register(binanceTradingRuleRegistry);

        OrderClientImpl orderClient = new OrderClientImpl(
                timeSynchronizer,
                orderEntryRest(baseRestAssistant, objectMapper),
                orderCancelRest(baseRestAssistant, objectMapper),
                tradingPairSymbolRegistry
        );

        ExchangeOrderExecutor exchangeOrderExecutor = new ExchangeOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                binanceTradingRuleRegistry,
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
        bootStrap.register(exchangeOrderExecutor);
        bootStrap.register(new OrderSnapshotUpdater(orderSnapshotRepository, eventBus));

        WsFundingInfoDataSource fundingInfoDataSource = new WsFundingInfoDataSource(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                eventBus,
                tradingPairSymbolRegistry,
                testnet ? ApiSpec.TESTNET_WSS_MARKET_URL : ApiSpec.WSS_MARKET_URL
        );
        bootStrap.register(fundingInfoDataSource);

        FundingInfoTracker fundingInfoTracker = new FundingInfoTracker(
                Exchange.BINANCE_DERIVATIVE,
                ApiSpec.DEFAULT_FUNDING_INTERVAL,
                "USDT",
                tradingPairSymbolRegistry,
                eventBus,
                eventBus,
                applicationEventPublisher,
                fundingRateHistoryDataSource,
                taskScheduler,
                fundingHistoryProperties
        );
        bootStrap.register(fundingInfoTracker);

        FundingIntervalDataSource fundingIntervalDataSource = new FundingIntervalDataSource(
                rest(baseRestAssistant, 2, objectMapper),
                tradingPairSymbolRegistry,
                eventBus,
                eventBus
        );
        bootStrap.register(fundingIntervalDataSource);
        bootStrap.register(new FundingIntervalFetchScheduler(taskScheduler, eventBus));

        DerivativeInfoTracker derivativeInfoTracker = new DerivativeInfoTracker(eventBus);
        bootStrap.register(derivativeInfoTracker);

        PositionTracker positionTracker = new PositionTracker(eventBus);
        bootStrap.register(positionTracker);

        FundingPaymentTracker fundingPaymentTracker = new FundingPaymentTracker(
                positionTracker,
                eventBus,
                eventBus
        );
        bootStrap.register(fundingPaymentTracker);
        bootStrap.register(new FundingPaymentSnapshotUpdater(fundingPaymentRepository, eventBus));

        DerivativeInfoDataSource derivativeInfoDataSource = new DerivativeInfoDataSource(
                rest(baseRestAssistant, 1, objectMapper),
                tradingPairSymbolRegistry,
                eventBus,
                eventBus
        );
        bootStrap.register(derivativeInfoDataSource);
        OrderStatusDataSource orderStatusDataSource = new OrderStatusDataSource(
                tradingPairSymbolRegistry,
                orderStatusRest(baseRestAssistant, objectMapper),
                eventBus,
                eventBus
        );
        bootStrap.register(orderStatusDataSource);
        OrderReconciliationCoordinator reconciliationCoordinator = new OrderReconciliationCoordinator(
                Exchange.BINANCE_DERIVATIVE,
                reconciliationTaskRepository,
                orderStatusDataSource,
                eventBus,
                taskScheduler
        );
        bootStrap.register(new ExchangeFailureCoordinator(
                eventBus,
                exchangeFailurePolicy,
                exchangeEligibilityRegistry,
                reconciliationCoordinator,
                eventBus
        ));
        bootStrap.register(reconciliationCoordinator);
        bootStrap.register(new ExchangeRecoveryProbe(
                Exchange.BINANCE_DERIVATIVE,
                exchangeEligibilityRegistry,
                reconciliationTaskRepository,
                orderStatusDataSource,
                eventBus,
                taskScheduler,
                orderTracker
        ));
        TradeDataSource tradeDataSource = new TradeDataSource(
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, 2, objectMapper),
                eventBus,
                eventBus
        );
        bootStrap.register(tradeDataSource);
        bootStrap.register(new OrderRecoveryBootstrap(
                Exchange.BINANCE_DERIVATIVE,
                orderSnapshotRepository,
                orderStatusDataSource,
                tradeDataSource,
                orderTracker
        ));
        bootStrap.register(new OrderStatusPoller(
                eventBus,
                eventBus,
                orderTracker,
                tradingPairSymbolRegistry,
                taskScheduler
        ));
        bootStrap.register(new TradePoller(eventBus, eventBus, taskScheduler, orderTracker));
        return new DerivativeExchangeConnector(
                Exchange.BINANCE_DERIVATIVE,
                ApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                binanceTradingRuleRegistry,
                exchangeOrderExecutor,
                eventBus,
                eventBus,
                new BinancePriceCandleDataSource(
                        Exchange.BINANCE_DERIVATIVE,
                        rest(baseRestAssistant, 2, objectMapper),
                        tradingPairSymbolRegistry,
                        ApiSpec.KLINE_PATH_URL
                ),
                fundingInfoTracker,
                positionTracker
        );
    }

    private static RestAssistant rest(
            RestAssistant delegate,
            int maxAttempt,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(maxAttempt)
                .build();
    }

    private static RestAssistant orderEntryRest(
            RestAssistant delegate,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .build();
    }

    private static RestAssistant orderCancelRest(
            RestAssistant delegate,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(1)
                .build();
    }

    private static RestAssistant orderStatusRest(
            RestAssistant delegate,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(2)
                .build();
    }
}
