package com.hotak.noonchibot.connector.derivative.bybit;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.StructuredOrderIdGenerator;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.connector.bybit.*;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Configuration
@Profile("!test")
public class BybitDerivativeConfig {

    @Bean
    public ExchangeEventBus bybitDerivativeEventBus(MainExecutor mainExecutor) {
        return new ExchangeEventBus(mainExecutor);
    }

    @Bean
    public RestClient bybitDerivativeRestClient() {
        return RestClient.builder().baseUrl(BybitDerivativeApiSpec.REST_BASE_URL).build();
    }

    @Bean
    public BybitAuthenticator bybitDerivativeAuthenticator(
            BybitConfig.BybitProperties bybitProperties,
            @Qualifier("bybitDerivativeTimeSynchronizer")
            TimeSynchronizer timeSynchronizer,
            ObjectMapper objectMapper
    ) {
        return new BybitAuthenticator(bybitProperties.apiKey(), bybitProperties.secretKey(), timeSynchronizer, objectMapper);
    }

    @Bean
    public RestAssistant bybitDerivativeRestAssistant(
            @Qualifier("bybitDerivativeRestClient")
            RestClient restClient,
            @Qualifier("bybitDerivativeAsyncThrottler")
            AsyncThrottler asyncThrottler,
            BybitAuthenticator bybitAuthenticator,
            ObjectMapper objectMapper
    ) {
        return new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor()),
                List.of(),
                bybitAuthenticator,
                asyncThrottler,
                objectMapper
        );
    }

    @Bean
    public WsAssistant bybitDerivativeWsAssistant(
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            @Qualifier("bybitDerivativeAuthenticator")
            BybitAuthenticator bybitAuthenticator
    ) {
        return new WsAssistant(
                webSocketClient,
                new WebSocketHttpHeaders(),
                List.of(),
                List.of(),
                objectMapper,
                bybitAuthenticator
        );
    }

    @Bean
    public TradingPairSymbolRegistry bybitDerivativeTradingPairSymbolRegistry() {
        return new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
    }

    @Bean
    public AsyncThrottler bybitDerivativeAsyncThrottler(
            IoExecutor ioExecutor
    ) {
        return new AsyncThrottlerImpl(BybitDerivativeApiSpec.RATE_LIMITS, ioExecutor);
    }

    @Bean
    public TimeSynchronizer bybitDerivativeTimeSynchronizer(
            @Qualifier("bybitDerivativeRestClient") RestClient restClient,
            @Qualifier("bybitDerivativeAsyncThrottler")
            AsyncThrottler asyncThrottler,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler
    ) {
        RestAssistant publicRestAssistant = new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor()),
                List.of(),
                null,
                asyncThrottler,
                objectMapper
        );
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(new BybitServerTimeProvider(publicRestAssistant, BybitDerivativeApiSpec.SERVER_TIME_PATH_URL), taskScheduler);
        timeSynchronizer.scheduleUpdate();
        return timeSynchronizer;
    }

    @Bean
    public BybitDerivativeOrderBookDataSource bybitDerivativeOrderBookDataSource(
            TaskScheduler taskScheduler,
            @Qualifier("bybitDerivativeWsAssistant") WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant
    ) {
        return new BybitDerivativeOrderBookDataSource(
                wsAssistant,
                BybitDerivativeApiSpec.WSS_LINEAR_URL,
                objectMapper,
                ioExecutor,
                taskScheduler,
                tradingPairSymbolRegistry,
                restAssistant
        );
    }

    @Bean
    public BybitTradingRuleRegistry bybitDerivativeTradingRuleRegistry(
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper
    ) {
        return new BybitTradingRuleRegistry(
                restAssistant,
                new BybitDerivativeTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                BybitDerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                BybitDerivativeApiSpec.EXCHANGE_INFO_PATH_URL
        );
    }

    @Bean
    public BybitDerivativeOrderExecutor bybitDerivativeOrderExecutor(
            BybitDerivativeOrderBookDataSource bybitDerivativeOrderBookDataSource,
            @Qualifier("bybitDerivativeOrderTracker") OrderTracker orderTracker,
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus exchangeEventBus,
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("bybitDerivativeTimeSynchronizer") TimeSynchronizer timeSynchronizer,
            @Qualifier("bybitDerivativeTradingRuleRegistry") TradingRuleRegistry tradingRuleRegistry,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor

    ) {
        return new BybitDerivativeOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                tradingRuleRegistry,
                tradingPairSymbolRegistry,
                bybitDerivativeOrderBookDataSource,
                timeSynchronizer,
                exchangeEventBus,
                restAssistant,
                mainExecutor,
                ioExecutor
        );
    }

    @Bean
    public BybitDerivativeBalancePoller bybitDerivativeBalancePoller(
            BybitDerivativeUserStreamEventPublisher bybitDerivativeUserStreamEventPublisher,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor,
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus eventBus,
            TaskScheduler taskScheduler
    ) {
        return new BybitDerivativeBalancePoller(
                bybitDerivativeUserStreamEventPublisher,
                ioExecutor,
                mainExecutor,
                restAssistant,
                eventBus,
                taskScheduler
        );
    }

    @Bean
    public OrderBookTracker bybitDerivativeOrderBookTracker(
            BybitDerivativeOrderBookDataSource bybitDerivativeOrderBookDataSource,
            TaskScheduler taskScheduler,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor
    ) {
        return new OrderBookTracker(bybitDerivativeOrderBookDataSource, taskScheduler, mainExecutor, ioExecutor, BybitDerivativeApiSpec.PLATFORM_NAME);
    }

    @Bean
    public AccountBalanceTracker bybitDerivativeAccountBalanceTracker(
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus eventBus
    ) {
        return new AccountBalanceTracker(BybitDerivativeApiSpec.PLATFORM_NAME, eventBus);
    }

    @Bean
    public OrderTracker bybitDerivativeOrderTracker(
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus eventBus,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository,
            IoExecutor ioExecutor
    ) {
        return new OrderTracker(
                eventBus,
                BybitDerivativeApiSpec.PLATFORM_NAME,
                tradeRepository,
                orderHistoryRepository,
                ioExecutor,
                eventBus
        );
    }

    @Bean
    @DependsOn({"bybitDerivativeAccountBalanceTracker", "bybitDerivativeOrderTracker"})
    public BybitDerivativeUserStreamEventPublisher bybitDerivativeUserStreamEventPublisher(
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitDerivativeWsAssistant") WsAssistant wsAssistant,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus eventBus,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            IoExecutor ioExecutor
    ) {
        return new BybitDerivativeUserStreamEventPublisher(
                wsAssistant,
                objectMapper,
                tradingPairSymbolRegistry,
                eventBus,
                ioExecutor
        );
    }

    @Bean
    public BybitOrderStatusPoller bybitDerivativeOrderStatusPoller(
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus eventBus,
            @Qualifier("bybitDerivativeOrderTracker") OrderTracker orderTracker,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            BybitDerivativeUserStreamEventPublisher userStreamEventPublisher,
            TaskScheduler taskScheduler
    ) {
        return new BybitOrderStatusPoller(
                restAssistant,
                eventBus,
                orderTracker,
                mainExecutor,
                ioExecutor,
                tradingPairSymbolRegistry,
                userStreamEventPublisher,
                taskScheduler,
                BybitDerivativeApiSpec.ORDER_REALTIME_PATH_URL
        );
    }

    @Bean
    public BybitTradePoller bybitDerivativeTradePoller(
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            TaskScheduler taskScheduler,
            @Qualifier("bybitDerivativeEventBus") ExchangeEventBus eventBus,
            @Qualifier("bybitDerivativeOrderTracker") OrderTracker orderTracker,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            BybitDerivativeUserStreamEventPublisher userStreamEventPublisher,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor
    ) {
        return new BybitTradePoller(
                userStreamEventPublisher,
                eventBus,
                restAssistant,
                taskScheduler,
                orderTracker,
                tradingPairSymbolRegistry,
                ioExecutor,
                mainExecutor,
                BybitDerivativeApiSpec.MY_TRADES_PATH_URL
        );
    }

    @Bean
    public BybitDerivativeTradeFeeSchemaLoader bybitDerivativeTradeFeeSchemaLoader(
            @Qualifier("bybitDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        return new BybitDerivativeTradeFeeSchemaLoader(restAssistant, tradingPairSymbolRegistry);
    }


}