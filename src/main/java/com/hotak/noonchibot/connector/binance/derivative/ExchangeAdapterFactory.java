package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.binance.*;
import com.hotak.noonchibot.connector.binance.derivative.BinanceDerivativeInfoDataSource;
import com.hotak.noonchibot.connector.binance.derivative.DerivativeApiSpec;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.throttle.ThrottlerLimitIdPreProcessor;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.derivative.DerivativeInfoTracker;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.event.Event;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.FailureAwareEventHandler;
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

class ExchangeAdapterFactory {
    public static DerivativeExchangeConnector create(
            BootStrap bootStrap,
            BinanceConfig.Properties props,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderHistoryRepository

    ) {
        EventBus eventBus = new EventBus();
        RestClient restClient = RestClient.builder().baseUrl(DerivativeApiSpec.REST_BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.derivative().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(DerivativeApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BinanceServerTimeProvider(
                        new RestAssistantImpl(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                        DerivativeApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
        bootStrap.register(timeSynchronizer);

        BinanceAuthenticator authenticator = new BinanceAuthenticator(props.apiKey(), props.secretKey(), timeSynchronizer, objectMapper);
        RestAssistant restAssistant = new RestAssistantImpl(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);
        OrderBookDataSource orderBookDataSource = new OrderBookDataSource(
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

        TradeFeeSchemaLoader feeSchemaLoader = new TradeFeeSchemaLoader(ioExecutor, tradingPairSymbolRegistry, restAssistant);
        lifeCycleRegistry.register(feeSchemaLoader);

        DerivativeUserStreamDataSource userStreamEventPublisher = new DerivativeUserStreamDataSource(
                restAssistant,
                wsAssistant,
                taskScheduler,
                objectMapper,
                tradingPairSymbolRegistry,
                eventBus,
                ioExecutor
        );
        lifeCycleRegistry.register(userStreamEventPublisher);

        RestBalanceDataSource balancePoller = new RestBalanceDataSource(
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

        DerivativeOrderClient orderExecutor = new DerivativeOrderClient(
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

        OrderStatusPoller orderStatusPoller = new OrderStatusPoller(
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

        TradePoller tradePoller = new TradePoller(
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

        BinanceWsFundingInfoDataSource fundingInfoDataSource = new BinanceWsFundingInfoDataSource(
                wsAssistant,
                DerivativeApiSpec.WSS_PUBLIC_URL,
                objectMapper,
                ioExecutor,
                tradingPairSymbolRegistry,
                restAssistant,
                taskScheduler
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

        BinanceDerivativeInfoDataSource derivativeAccountConfigurer = new BinanceDerivativeInfoDataSource(
                derivativeInfoTracker,
                eventBus,
                restAssistant,
                tradingPairSymbolRegistry,
                ioExecutor
        );

        BinanceDerivativeInfoDataSource derivativeAccountClient = new BinanceDerivativeInfoDataSource(
                restAssistant,
                tradingPairSymbolRegistry,
                eventBus
        );

        EventHandler<LeverageChangeIORequest> leverageChangeIORequestHandler = new FailureAwareEventHandler<>() {
            @Override
            public Event onFailure(LeverageChangeIORequest event, Throwable cause) {
                return null;
            }

            @Override
            public void onEvent(LeverageChangeIORequest event) {

            }
        }

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
                derivativeAccountConfigurer
        );
    }
}
