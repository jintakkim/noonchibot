package com.hotak.noonchibot.connector.binance;

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
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

class DerivativeExchangeAdapterFactory {
    public static DerivativeExchangeAdapter create(
            BinanceConfig.Properties props,
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
        RestClient restClient = RestClient.builder().baseUrl(DerivativeApiSpec.REST_BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.derivative().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(DerivativeApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BinanceServerTimeProvider(
                        new RestAssistant(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                        DerivativeApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
        lifeCycleRegistry.register(timeSynchronizer);

        BinanceAuthenticator authenticator = new BinanceAuthenticator(props.apiKey(), props.secretKey(), timeSynchronizer, objectMapper);
        RestAssistant restAssistant = new RestAssistant(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);
        WsAssistant wsAssistant = new WsAssistant(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        DerivativeOrderBookDataSource orderBookDataSource = new DerivativeOrderBookDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_PUBLIC_URL,
                objectMapper,
                ioExecutor,
                taskScheduler,
                tradingPairSymbolRegistry,
                restAssistant
        );
        lifeCycleRegistry.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, DerivativeApiSpec.PLATFORM_NAME, tradeRepository, orderHistoryRepository, ioExecutor, eventBus);
        lifeCycleRegistry.register(orderTracker);

        OrderBookTracker orderBookTracker = new OrderBookTracker(orderBookDataSource, mainExecutor, ioExecutor);
        lifeCycleRegistry.register(orderBookTracker);

        AccountBalanceTracker accountBalanceTracker = new AccountBalanceTracker(eventBus);
        lifeCycleRegistry.register(accountBalanceTracker);

        TradeFeeSchemaLoader feeSchemaLoader = new DerivativeTradeFeeSchemaLoader(ioExecutor, mainExecutor, tradingPairSymbolRegistry, restAssistant);
        DerivativeUserStreamEventPublisher userStreamEventPublisher = new DerivativeUserStreamEventPublisher(
                restAssistant,
                wsAssistant,
                taskScheduler,
                objectMapper,
                tradingPairSymbolRegistry,
                eventBus,
                ioExecutor
        );
        lifeCycleRegistry.register(userStreamEventPublisher);

        DerivativeBalancePoller balancePoller = new DerivativeBalancePoller(
                userStreamEventPublisher,
                ioExecutor,
                restAssistant,
                eventBus,
                taskScheduler
        );
        lifeCycleRegistry.register(balancePoller);

        BinanceTradingRuleRegistry binanceTradingRuleRegistry = new BinanceTradingRuleRegistry(
                restAssistant,
                new DerivativeTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                DerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                DerivativeApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper
        );
        lifeCycleRegistry.register(binanceTradingRuleRegistry);

        DerivativeOrderExecutor orderExecutor = new DerivativeOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                binanceTradingRuleRegistry,
                tradingPairSymbolRegistry,
                orderBookTracker,
                timeSynchronizer,
                eventBus,
                restAssistant,
                mainExecutor,
                ioExecutor
        );

        DerivativeOrderStatusPoller orderStatusPoller = new DerivativeOrderStatusPoller(
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

        DerivativeTradePoller tradePoller = new DerivativeTradePoller(
                userStreamEventPublisher,
                eventBus,
                restAssistant,
                taskScheduler,
                orderTracker,
                tradingPairSymbolRegistry,
                ioExecutor,
                mainExecutor
        );
        lifeCycleRegistry.register(tradePoller);

        return new DerivativeExchangeAdapter(
                DerivativeApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                binanceTradingRuleRegistry,
                orderExecutor,
                lifeCycleRegistry
        );
    }
}
