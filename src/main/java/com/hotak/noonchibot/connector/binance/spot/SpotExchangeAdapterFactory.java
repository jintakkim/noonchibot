package com.hotak.noonchibot.connector.binance.spot;

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
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotUpdater;
import com.hotak.noonchibot.core.order.OrderRecoveryBootstrap;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.strategy.safety.TradingSafetyController;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;

public class SpotExchangeAdapterFactory {
    public static ExchangeConnector create(
            BootStrap bootStrap,
            BinanceConfig.Properties props,
            OrderSnapshotRepository orderSnapshotRepository,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            TradingSafetyController tradingSafetyController,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        EventBus eventBus = new EventBus();
        tradingSafetyController.connect(eventBus);
        boolean testnet = props.network().isTestnet();
        RestClient restClient = RestClient.builder()
                .baseUrl(testnet ? ApiSpec.TESTNET_REST_BASE_URL : ApiSpec.REST_BASE_URL)
                .build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
                props.spot().tradingPairSymbolMap()
        );
        AsyncThrottler throttler = new AsyncThrottlerImpl(ApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BinanceServerTimeProvider(
                        rest(
                                new RestAssistantImpl(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                                circuitBreakerRegistry,
                                CircuitBreakerNames.serverTime(Exchange.BINANCE_SPOT),
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
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        OrderBookDataSource orderBookDataSource = new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.orderBook(Exchange.BINANCE_SPOT), 2, objectMapper),
                timeSynchronizer,
                testnet ? ApiSpec.TESTNET_WSS_URL : ApiSpec.WSS_URL,
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
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradeFee(Exchange.BINANCE_SPOT), 2, objectMapper)
        );
        bootStrap.register(feeSchemaLoader);

        UserStreamDataSource userStreamDataSource = new UserStreamDataSource(
                wsAssistant,
                objectMapper,
                authenticator,
                tradingPairSymbolRegistry,
                eventBus,
                taskScheduler,
                applicationEventPublisher,
                testnet ? ApiSpec.TESTNET_WSS_API_URL : ApiSpec.WSS_API_URL
        );
        bootStrap.register(userStreamDataSource);

        RestBalanceDataSource balanceDataSource = new RestBalanceDataSource(
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.balance(Exchange.BINANCE_SPOT), 2, objectMapper),
                eventBus,
                taskScheduler
        );
        bootStrap.register(balanceDataSource);

        BinanceTradingRuleRegistry tradingRuleRegistry = new BinanceTradingRuleRegistry(
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradingRules(Exchange.BINANCE_SPOT), 2, objectMapper),
                new TradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                ApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                ApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper
        );
        bootStrap.register(tradingRuleRegistry);

        OrderClientImpl orderClient = new OrderClientImpl(
                timeSynchronizer,
                orderEntryRest(baseRestAssistant, circuitBreakerRegistry, objectMapper),
                orderCancelRest(baseRestAssistant, circuitBreakerRegistry, objectMapper),
                tradingPairSymbolRegistry
        );
        ExchangeOrderExecutor exchangeOrderExecutor = new ExchangeOrderExecutor(
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
                Exchange.BINANCE_SPOT,
                orderSnapshotRepository
        );
        bootStrap.register(exchangeOrderExecutor);
        bootStrap.register(new OrderSnapshotUpdater(orderSnapshotRepository, eventBus));

        OrderStatusDataSource orderStatusDataSource = new OrderStatusDataSource(
                tradingPairSymbolRegistry,
                orderStatusRest(baseRestAssistant, circuitBreakerRegistry, objectMapper),
                eventBus,
                eventBus
        );
        bootStrap.register(orderStatusDataSource);
        TradeDataSource tradeDataSource = new TradeDataSource(
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.trades(Exchange.BINANCE_SPOT), 2, objectMapper),
                eventBus,
                eventBus
        );
        bootStrap.register(tradeDataSource);
        bootStrap.register(new OrderRecoveryBootstrap(
                Exchange.BINANCE_SPOT,
                orderSnapshotRepository,
                orderStatusDataSource,
                tradeDataSource,
                orderTracker
        ));
        bootStrap.register(new OrderStatusPoller(eventBus, eventBus, orderTracker, taskScheduler));
        bootStrap.register(new TradePoller(eventBus, eventBus, taskScheduler, orderTracker));

        PriceCandleDataSource priceCandleDataSource = new BinancePriceCandleDataSource(
                Exchange.BINANCE_SPOT,
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.rest(Exchange.BINANCE_SPOT), 2, objectMapper),
                tradingPairSymbolRegistry,
                ApiSpec.KLINE_PATH_URL
        );
        return new ExchangeConnector(
                Exchange.BINANCE_SPOT,
                ApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                tradingRuleRegistry,
                exchangeOrderExecutor,
                eventBus,
                priceCandleDataSource
        );
    }

    private static RestAssistant rest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            String circuitName,
            int maxAttempt,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, circuitName)
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(maxAttempt)
                .build();
    }

    private static RestAssistant orderEntryRest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderEntry(Exchange.BINANCE_SPOT))
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .build();
    }

    private static RestAssistant orderCancelRest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderCancel(Exchange.BINANCE_SPOT))
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(1)
                .build();
    }

    private static RestAssistant orderStatusRest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderStatus(Exchange.BINANCE_SPOT))
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(2)
                .build();
    }
}
