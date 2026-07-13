package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.binance.*;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.config.BotConstants;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotUpdater;
import com.hotak.noonchibot.core.order.OrderRecoveryBootstrap;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.strategy.safety.TradingSafetyController;
import io.micrometer.core.instrument.MeterRegistry;
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
            MeterRegistry meterRegistry
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
                        new MeteredRestAssistant(
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
                                Exchange.BINANCE_SPOT.name()
                        ),
                        ApiSpec.SERVER_TIME_PATH_URL),
                taskScheduler
        );
        bootStrap.register(timeSynchronizer);

        BinanceAuthenticator authenticator = new BinanceAuthenticator(props.apiKey(), props.secretKey(), timeSynchronizer, objectMapper);
        RestAssistant rawRestAssistant = new MeteredRestAssistant(
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
                Exchange.BINANCE_SPOT.name()
        );
        RestAssistant baseRestAssistant = new TimestampRecoveringRestAssistant(
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
                baseRestAssistant,
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
                baseRestAssistant
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
                baseRestAssistant,
                eventBus,
                taskScheduler
        );
        bootStrap.register(balanceDataSource);

        BinanceTradingRuleRegistry tradingRuleRegistry = new BinanceTradingRuleRegistry(
                baseRestAssistant,
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
                baseRestAssistant,
                baseRestAssistant,
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
                baseRestAssistant,
                eventBus,
                eventBus
        );
        bootStrap.register(orderStatusDataSource);
        TradeDataSource tradeDataSource = new TradeDataSource(
                tradingPairSymbolRegistry,
                baseRestAssistant,
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
                baseRestAssistant,
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

}
