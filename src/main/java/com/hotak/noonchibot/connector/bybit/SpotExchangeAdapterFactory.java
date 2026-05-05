package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.throttle.ThrottlerLimitIdPreProcessor;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.event.ExchangeEventBus;
import com.hotak.noonchibot.core.order.OrderHistoryRepository;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

class SpotExchangeAdapterFactory {
    public static ExchangeConnector create(
            BybitConfig.Properties props,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository

    ) {
        ExchangeLifeCycleRegistry lifeCycleRegistry = new ExchangeLifeCycleRegistry();
        ExchangeEventBus eventBus = new ExchangeEventBus(mainExecutor);
        RestClient restClient = RestClient.builder().baseUrl(SpotApiSpec.REST_BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.spot().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(SpotApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BybitServerTimeProvider(
                        new RestAssistant(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                        SpotApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
        lifeCycleRegistry.register(timeSynchronizer);

        BybitAuthenticator authenticator = new BybitAuthenticator(props.apiKey(), props.secretKey(), timeSynchronizer, objectMapper);
        RestAssistant restAssistant = new RestAssistant(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);
        WsAssistant wsAssistant = new WsAssistant(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        SpotOrderBookDataSource orderBookDataSource = new SpotOrderBookDataSource(wsAssistant, SpotApiSpec.WSS_URL, objectMapper, ioExecutor, taskScheduler, tradingPairSymbolRegistry, restAssistant);
        lifeCycleRegistry.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, SpotApiSpec.PLATFORM_NAME, tradeRepository, orderHistoryRepository, ioExecutor, eventBus);
        lifeCycleRegistry.register(orderTracker);

        OrderBookTracker orderBookTracker = new OrderBookTracker(orderBookDataSource, mainExecutor, ioExecutor);
        lifeCycleRegistry.register(orderBookTracker);

        AccountBalanceTracker accountBalanceTracker = new AccountBalanceTracker(eventBus);
        lifeCycleRegistry.register(accountBalanceTracker);

        SpotTradeFeeSchemaLoader feeSchemaLoader = new SpotTradeFeeSchemaLoader(ioExecutor, tradingPairSymbolRegistry, restAssistant);
        lifeCycleRegistry.register(feeSchemaLoader);

        SpotUserStreamEventPublisher userStreamEventPublisher = new SpotUserStreamEventPublisher(
                wsAssistant,
                objectMapper,
                authenticator,
                List.of(new SpotExecutionReportParser(tradingPairSymbolRegistry), new SpotBalanceUpdateParser()),
                eventBus,
                ioExecutor
        );
        lifeCycleRegistry.register(userStreamEventPublisher);

        SpotBalancePoller spotBalancePoller = new SpotBalancePoller(
                userStreamEventPublisher,
                ioExecutor,
                restAssistant,
                eventBus,
                taskScheduler
        );
        lifeCycleRegistry.register(spotBalancePoller);

        BybitTradingRuleRegistry bybitTradingRuleRegistry = new BybitTradingRuleRegistry(
                restAssistant,
                new SpotTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                SpotApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                SpotApiSpec.EXCHANGE_INFO_PATH_URL
        );
        lifeCycleRegistry.register(bybitTradingRuleRegistry);

        SpotOrderExecutor orderExecutor = new SpotOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                bybitTradingRuleRegistry,
                tradingPairSymbolRegistry,
                orderBookTracker,
                timeSynchronizer,
                eventBus,
                restAssistant,
                mainExecutor,
                ioExecutor
        );

        SpotOrderStatusPoller orderStatusPoller = new SpotOrderStatusPoller(
                restAssistant,
                eventBus,
                orderTracker,
                mainExecutor,
                ioExecutor,
                tradingPairSymbolRegistry,
                userStreamEventPublisher,
                taskScheduler
        );
        lifeCycleRegistry.register(orderStatusPoller);

        SpotTradePoller tradePoller = new SpotTradePoller(
                userStreamEventPublisher,
                eventBus,
                restAssistant,
                taskScheduler,
                orderTracker,
                tradingPairSymbolRegistry,
                ioExecutor,
                mainExecutor,
                SpotApiSpec.MY_TRADES_PATH_URL
        );
        lifeCycleRegistry.register(tradePoller);

        return new ExchangeConnector(
                SpotApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                bybitTradingRuleRegistry,
                orderExecutor,
                lifeCycleRegistry
        );
    }
}

