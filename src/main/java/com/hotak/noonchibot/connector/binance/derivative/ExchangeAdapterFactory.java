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
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.FundingPaymentRepository;
import com.hotak.noonchibot.core.derivative.FundingPaymentSnapshotUpdater;
import com.hotak.noonchibot.core.derivative.FundingPaymentTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;

public class ExchangeAdapterFactory {
    public static DerivativeExchangeConnector create(
            BootStrap bootStrap,
            BinanceConfig.Properties props,
            OrderSnapshotRepository orderSnapshotRepository,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            FundingPaymentRepository fundingPaymentRepository,
            OrderSnapshotRepository orderHistoryRepository,
            CircuitBreakerRegistry circuitBreakerRegistry

    ) {
        EventBus eventBus = new EventBus();
        RestClient restClient = RestClient.builder().baseUrl(ApiSpec.REST_BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(props.derivative().tradingPairSymbolMap());
        AsyncThrottler throttler = new AsyncThrottlerImpl(ApiSpec.RATE_LIMITS, ioExecutor);
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(
                new BinanceServerTimeProvider(
                        new RestAssistantImpl(restClient, List.of(new ThrottlerLimitIdPreProcessor()), List.of(), null, throttler, objectMapper),
                        ApiSpec.SERVER_TIME_PATH_URL,
                        Exchange.BINANCE_DERIVATIVE,
                        circuitBreakerRegistry),
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
                rawRestAssistant,
                eventBus,
                eventBus,
                circuitBreakerRegistry
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
                rawRestAssistant,
                circuitBreakerRegistry
        );
        bootStrap.register(feeSchemaLoader);

        UserStreamDataSource userStreamDataSource = new UserStreamDataSource(
                rawRestAssistant,
                wsAssistant,
                taskScheduler,
                objectMapper,
                tradingPairSymbolRegistry,
                eventBus,
                ioExecutor,
                circuitBreakerRegistry
        );
        bootStrap.register(userStreamDataSource);

        RestBalanceDataSource balancePoller = new RestBalanceDataSource(
                rawRestAssistant,
                eventBus,
                taskScheduler,
                circuitBreakerRegistry
        );
        bootStrap.register(balancePoller);

        BinanceTradingRuleRegistry binanceTradingRuleRegistry = new BinanceTradingRuleRegistry(
                rawRestAssistant,
                new DerivativeTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                ApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                ApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper,
                Exchange.BINANCE_DERIVATIVE,
                circuitBreakerRegistry
        );
        bootStrap.register(binanceTradingRuleRegistry);

        OrderClientImpl orderClient = new OrderClientImpl(
                timeSynchronizer,
                rawRestAssistant,
                tradingPairSymbolRegistry,
                circuitBreakerRegistry
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

        OrderStatusPoller orderStatusPoller = new OrderStatusPoller(
                eventBus,
                orderTracker,
                tradingPairSymbolRegistry,
                taskScheduler
        );
        bootStrap.register(orderStatusPoller);

        TradePoller tradePoller = new TradePoller(
                eventBus,
                taskScheduler,
                orderTracker
        );
        bootStrap.register(tradePoller);

        WsFundingInfoDataSource fundingInfoDataSource = new WsFundingInfoDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                eventBus,
                tradingPairSymbolRegistry
        );
        bootStrap.register(fundingInfoDataSource);

        FundingInfoTracker fundingInfoTracker = new FundingInfoTracker(
                ApiSpec.DEFAULT_FUNDING_INTERVAL,
                "USDT",
                tradingPairSymbolRegistry,
                eventBus
        );
        bootStrap.register(fundingInfoTracker);

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
                rawRestAssistant,
                tradingPairSymbolRegistry,
                eventBus,
                eventBus,
                circuitBreakerRegistry
        );
        bootStrap.register(derivativeInfoDataSource);


       OrderStatusDataSource orderStatusDataSource = new OrderStatusDataSource(
                tradingPairSymbolRegistry,
                rawRestAssistant,
                eventBus,
                eventBus,
                circuitBreakerRegistry
        );
        bootStrap.register(orderStatusDataSource);

        TradeDataSource tradeDataSource = new TradeDataSource(
                tradingPairSymbolRegistry,
                rawRestAssistant,
                eventBus,
                eventBus,
                circuitBreakerRegistry
        );
        bootStrap.register(tradeDataSource);

        bootStrap.register(new OrderStatusPoller(eventBus, orderTracker, tradingPairSymbolRegistry, taskScheduler));
        bootStrap.register(new TradePoller(eventBus, taskScheduler, orderTracker));



        return new DerivativeExchangeConnector(
                ApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                binanceTradingRuleRegistry,
                exchangeOrderExecutor,
                fundingInfoTracker,
                positionTracker
        );
    }

}
