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
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;

public class ExchangeAdapterFactory {
    public static ExchangeConnector create(
            BootStrap bootStrap,
            BinanceConfig.Properties props,
            OrderSnapshotRepository orderSnapshotRepository,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderHistoryRepository,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        EventBus eventBus = new EventBus();
        RestClient restClient = RestClient.builder().baseUrl(ApiSpec.REST_BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.spot().tradingPairSymbolMap());
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

        RestAssistant rawRestAssistant = new RestAssistantImpl(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);

        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        OrderBookDataSource orderBookDataSource = new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                tradingPairSymbolRegistry,
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.orderBook(Exchange.BINANCE_SPOT), 2, objectMapper),
                timeSynchronizer,
                eventBus,
                eventBus
        );
        bootStrap.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, tradeRepository, orderHistoryRepository, eventBus);
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
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradeFee(Exchange.BINANCE_SPOT), 2, objectMapper)
        );
        bootStrap.register(feeSchemaLoader);

        UserStreamDataSource userStreamDataSource = new UserStreamDataSource(
                wsAssistant,
                objectMapper,
                authenticator,
                tradingPairSymbolRegistry,
                eventBus,
                ioExecutor
        );
        bootStrap.register(userStreamDataSource);

        RestBalanceDataSource balanceDataSource = new RestBalanceDataSource(
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.balance(Exchange.BINANCE_SPOT), 2, objectMapper),
                eventBus,
                taskScheduler
        );
        bootStrap.register(balanceDataSource);

        BinanceTradingRuleRegistry tradingRuleRegistry = new BinanceTradingRuleRegistry(
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradingRules(Exchange.BINANCE_SPOT), 2, objectMapper),
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
                orderEntryRest(rawRestAssistant, circuitBreakerRegistry, objectMapper),
                orderCancelRest(rawRestAssistant, circuitBreakerRegistry, objectMapper),
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

        OrderStatusDataSource orderStatusDataSource = new OrderStatusDataSource(
                tradingPairSymbolRegistry,
                orderStatusRest(rawRestAssistant, circuitBreakerRegistry, objectMapper),
                eventBus,
                eventBus
        );
        bootStrap.register(orderStatusDataSource);

        TradeDataSource tradeDataSource = new TradeDataSource(
                tradingPairSymbolRegistry,
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.trades(Exchange.BINANCE_SPOT), 2, objectMapper),
                eventBus,
                eventBus
        );
        bootStrap.register(tradeDataSource);

        bootStrap.register(new OrderStatusPoller(eventBus, orderTracker, taskScheduler));
        bootStrap.register(new TradePoller(eventBus, taskScheduler, orderTracker));

        return new ExchangeConnector(
                ApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                tradingRuleRegistry,
                exchangeOrderExecutor
        );
    }

    private static RestAssistant rest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry,
            String circuitName,
            int maxAttempt,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, circuitName)
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(maxAttempt)
                .build();
    }

    private static RestAssistant orderEntryRest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderEntry(Exchange.BINANCE_SPOT))
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .build();
    }

    private static RestAssistant orderCancelRest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderCancel(Exchange.BINANCE_SPOT))
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(1)
                .build();
    }

    private static RestAssistant orderStatusRest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ObjectMapper objectMapper
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderStatus(Exchange.BINANCE_SPOT))
                .errorClassifier(new BinanceExchangeErrorClassifier(objectMapper))
                .maxAttempt(2)
                .build();
    }
}
