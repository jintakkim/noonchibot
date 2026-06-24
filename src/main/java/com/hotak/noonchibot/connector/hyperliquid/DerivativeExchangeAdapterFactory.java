package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.binance.*;
import com.hotak.noonchibot.connector.binance.derivative.TradePoller;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
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
import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class DerivativeExchangeAdapterFactory {
    public static DerivativeExchangeConnector create(
            HyperliquidConfig.Properties props,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            MessagePackMapper messagePackMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            OrderSnapshotRepository orderHistoryRepository

    ) {
        ExchangeLifeCycleRegistry lifeCycleRegistry = new ExchangeLifeCycleRegistry();
        EventBus eventBus = new EventBus(mainExecutor);
        RestClient restClient = RestClient.builder().baseUrl(DerivativeApiSpec.BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.derivative().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(com.hotak.noonchibot.connector.hyperliquid.DerivativeApiSpec.RATE_LIMITS, ioExecutor);
        HyperliquidAuthenticator authenticator = new HyperliquidAuthenticator(
                objectMapper,
                messagePackMapper,
                null,
                true,
                props.address(),
                props.secret()
        );
        RestAssistantImpl restAssistant = new RestAssistantImpl(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);
        DerivativeOrderBookDataSource orderBookDataSource = new DerivativeOrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                restAssistant,
                tradingPairSymbolRegistry
        );
        lifeCycleRegistry.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, DerivativeApiSpec.PLATFORM_NAME, tradeRepository, orderHistoryRepository, ioExecutor, eventBus);
        lifeCycleRegistry.register(orderTracker);

        OrderBookTracker orderBookTracker = new OrderBookTracker(orderBookDataSource, mainExecutor, ioExecutor);
        lifeCycleRegistry.register(orderBookTracker);

        AccountBalanceTracker accountBalanceTracker = new AccountBalanceTracker(eventBus);
        lifeCycleRegistry.register(accountBalanceTracker);

        DerivativeTradeFeeSchemaLoader feeSchemaLoader = new DerivativeTradeFeeSchemaLoader(ioExecutor, tradingPairSymbolRegistry, restAssistant, props.address());
        lifeCycleRegistry.register(feeSchemaLoader);

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
                com.hotak.noonchibot.connector.binance.DerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                com.hotak.noonchibot.connector.binance.DerivativeApiSpec.EXCHANGE_INFO_PATH_URL,
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

        com.hotak.noonchibot.connector.binance.DerivativeOrderStatusPoller orderStatusPoller = new com.hotak.noonchibot.connector.binance.DerivativeOrderStatusPoller(
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
                com.hotak.noonchibot.connector.binance.DerivativeApiSpec.WSS_PUBLIC_URL,
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

        BinanceDerivativeAccountClient derivativeAccountConfigurer = new BinanceDerivativeAccountClient(
                derivativeInfoTracker,
                eventBus,
                restAssistant,
                tradingPairSymbolRegistry,
                ioExecutor
        );


        return new DerivativeExchangeConnector(
                com.hotak.noonchibot.connector.binance.DerivativeApiSpec.PLATFORM_NAME,
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
