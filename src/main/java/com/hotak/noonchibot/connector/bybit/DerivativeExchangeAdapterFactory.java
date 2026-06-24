package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.throttle.ThrottlerLimitIdPreProcessor;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.derivative.DerivativeInfoTracker;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

class DerivativeExchangeAdapterFactory {
    public static DerivativeExchangeConnector create(
            BybitConfig.Properties props,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderHistoryRepository

    ) {
        ExchangeLifeCycleRegistry lifeCycleRegistry = new ExchangeLifeCycleRegistry();
        EventBus eventBus = new EventBus(mainExecutor);
        RestClient restClient = RestClient.builder().baseUrl(DerivativeApiSpec.REST_BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.derivative().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(DerivativeApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BybitServerTimeProvider(
                        new RestAssistantImpl(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                        DerivativeApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
        lifeCycleRegistry.register(timeSynchronizer);

        BybitAuthenticator authenticator = new BybitAuthenticator(props.apiKey(), props.secretKey(), timeSynchronizer, objectMapper);
        RestAssistantImpl restAssistant = new RestAssistantImpl(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        DerivativeOrderBookDataSource orderBookDataSource = new DerivativeOrderBookDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_LINEAR_URL,
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

        DerivativeTradeFeeSchemaLoader feeSchemaLoader = new DerivativeTradeFeeSchemaLoader(ioExecutor, tradingPairSymbolRegistry, restAssistant);
        lifeCycleRegistry.register(feeSchemaLoader);

        DerivativeUserStreamEventPublisher userStreamEventPublisher = new DerivativeUserStreamEventPublisher(
                wsAssistant,
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

        BybitTradingRuleRegistry binanceTradingRuleRegistry = new BybitTradingRuleRegistry(
                restAssistant,
                new DerivativeTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                DerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                DerivativeApiSpec.EXCHANGE_INFO_PATH_URL
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

        BybitWsFundingInfoDataSource fundingInfoDataSource = new BybitWsFundingInfoDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_LINEAR_URL,
                objectMapper,
                ioExecutor,
                tradingPairSymbolRegistry,
                restAssistant
        );
        lifeCycleRegistry.register(fundingInfoDataSource);

        FundingInfoTracker fundingInfoTracker = new FundingInfoTracker(
                "USDT",
                tradingPairSymbolRegistry,
                fundingInfoDataSource,
                ioExecutor,
                mainExecutor
        );
        lifeCycleRegistry.register(fundingInfoTracker);

        DerivativeInfoTracker derivativeInfoTracker = new DerivativeInfoTracker(eventBus);
        lifeCycleRegistry.register(derivativeInfoTracker);

        BybitDerivativeAccountConfigurer configurer = new BybitDerivativeAccountConfigurer(
                derivativeInfoTracker,
                eventBus,
                ioExecutor,
                restAssistant,
                tradingPairSymbolRegistry
        );

        return new DerivativeExchangeConnector(
                DerivativeApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                binanceTradingRuleRegistry,
                orderExecutor,
                lifeCycleRegistry,
                fundingInfoTracker,
                configurer
        );
    }
}
