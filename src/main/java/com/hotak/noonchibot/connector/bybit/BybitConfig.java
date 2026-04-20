package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.StructuredOrderIdGenerator;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
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
import org.springframework.boot.context.properties.ConfigurationProperties;
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
@EnableConfigurationProperties(BybitConfig.BybitProperties.class)
public class BybitConfig {

    @ConfigurationProperties(prefix = "bybit")
    public record BybitProperties(
            String apiKey,
            String secretKey
    ) {}

    @Bean
    public ExchangeEventBus bybitEventBus(MainExecutor mainExecutor) { return new ExchangeEventBus(mainExecutor); }

    @Bean
    public RestClient bybitRestClient() { return RestClient.builder().baseUrl(BybitApiSpec.REST_BASE_URL).build(); }

    @Bean
    public BybitAuthenticator bybitAuthenticator(
            BybitProperties bybitProperties,
            @Qualifier("bybitTimeSynchronizer")
            TimeSynchronizer timeSynchronizer,
            ObjectMapper objectMapper
    ) {
        return new BybitAuthenticator(bybitProperties.apiKey, bybitProperties.secretKey, timeSynchronizer, objectMapper);
    }

    @Bean
    public RestAssistant bybitRestAssistant(
            @Qualifier("bybitRestClient")
            RestClient restClient,
            @Qualifier("bybitAsyncThrottler")
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
    public WsAssistant bybitWsAssistant(
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            @Qualifier("bybitAuthenticator")
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
    public TradingPairSymbolRegistry bybitTradingPairSymbolRegistry() {
        return new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
    }

    @Bean
    public AsyncThrottler bybitAsyncThrottler(IoExecutor ioExecutor) {
        return new AsyncThrottlerImpl(BybitApiSpec.RATE_LIMITS, ioExecutor);
    }

    @Bean
    public TimeSynchronizer bybitTimeSynchronizer(
            @Qualifier("bybitRestClient")
            RestClient restClient,
            @Qualifier("bybitAsyncThrottler")
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
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(new BybitServerTimeProvider(publicRestAssistant, BybitApiSpec.SERVER_TIME_PATH_URL), taskScheduler);
        timeSynchronizer.scheduleUpdate();
        return timeSynchronizer;
    }

    @Bean
    public BybitOrderBookDataSource bybitOrderBookDataSource(
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitWsAssistant") WsAssistant wsAssistant,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            @Qualifier("bybitTimeSynchronizer") TimeSynchronizer timeSynchronizer
    ) {
        return new BybitOrderBookDataSource(
                wsAssistant,
                BybitApiSpec.WSS_URL,
                objectMapper,
                ioExecutor,
                taskScheduler,
                tradingPairSymbolRegistry,
                restAssistant,
                timeSynchronizer
        );
    }

    @Bean
    public BybitTradingRuleRegistry bybitTradingRuleRegistry(
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper
    ) {
        return new BybitTradingRuleRegistry(
                restAssistant,
                new BybitTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                BybitApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                BybitApiSpec.EXCHANGE_INFO_PATH_URL
        );
    }

    @Bean
    public BybitOrderExecutor bybitOrderExecutor(
            BybitOrderBookDataSource bybitOrderBookDataSource,
            @Qualifier("bybitOrderTracker") OrderTracker orderTracker,
            @Qualifier("bybitEventBus") ExchangeEventBus exchangeEventBus,
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("bybitTimeSynchronizer") TimeSynchronizer timeSynchronizer,
            @Qualifier("bybitTradingRuleRegistry") TradingRuleRegistry tradingRuleRegistry,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor

    ) {
        return new BybitOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                tradingRuleRegistry,
                tradingPairSymbolRegistry,
                bybitOrderBookDataSource,
                timeSynchronizer,
                exchangeEventBus,
                restAssistant,
                mainExecutor,
                ioExecutor
        );
    }

    @Bean
    public BybitBalancePoller bybitBalancePoller(
            @Qualifier("bybitUserStreamEventPublisher") BybitUserStreamEventPublisher bybitUserStreamEventPublisher,
            IoExecutor ioExecutor,
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitEventBus") ExchangeEventBus eventBus,
            TaskScheduler taskScheduler
    ) {
        return new BybitBalancePoller(
                bybitUserStreamEventPublisher,
                ioExecutor,
                restAssistant,
                eventBus,
                taskScheduler
        );
    }

    @Bean
    public OrderBookTracker bybitOrderBookTracker(
            BybitOrderBookDataSource bybitOrderBookDataSource,
            TaskScheduler taskScheduler,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor
    ) {
        return new OrderBookTracker(bybitOrderBookDataSource, taskScheduler, mainExecutor, ioExecutor, BybitApiSpec.PLATFORM_NAME);
    }

    @Bean
    public AccountBalanceTracker bybitAccountBalanceTracker(@Qualifier("bybitEventBus") ExchangeEventBus eventBus) {
        return new AccountBalanceTracker(BybitApiSpec.PLATFORM_NAME, eventBus);
    }

    @Bean
    public OrderTracker bybitOrderTracker(
            @Qualifier("bybitEventBus") ExchangeEventBus eventBus,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository,
            IoExecutor ioExecutor
            ) {
        return new OrderTracker(
                eventBus,
                BybitApiSpec.PLATFORM_NAME,
                tradeRepository,
                orderHistoryRepository,
                ioExecutor,
                eventBus
        );
    }

    @Bean
    @DependsOn({"bybitAccountBalanceTracker", "bybitOrderTracker"})
    public BybitUserStreamEventPublisher bybitUserStreamEventPublisher(
            @Qualifier("bybitWsAssistant") WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            BybitAuthenticator bybitAuthenticator,
            @Qualifier("bybitEventBus") ExchangeEventBus eventBus,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            IoExecutor ioExecutor
    ) {
        return new BybitUserStreamEventPublisher(
                wsAssistant,
                objectMapper,
                bybitAuthenticator,
                List.of(new BybitExecutionReportParser(tradingPairSymbolRegistry), new BybitBalanceUpdateParser()),
                eventBus,
                ioExecutor
        );
    }

    @Bean
    public BybitOrderStatusPoller bybitOrderStatusPoller(
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitEventBus") ExchangeEventBus eventBus,
            @Qualifier("bybitOrderTracker") OrderTracker orderTracker,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("bybitUserStreamEventPublisher") BybitUserStreamEventPublisher userStreamEventPublisher,
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
                BybitApiSpec.ORDER_REALTIME_PATH_URL
        );
    }

    @Bean
    public BybitTradePoller bybitTradePoller(
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            TaskScheduler taskScheduler,
            @Qualifier("bybitEventBus") ExchangeEventBus eventBus,
            @Qualifier("bybitOrderTracker") OrderTracker orderTracker,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("bybitUserStreamEventPublisher") BybitUserStreamEventPublisher userStreamEventPublisher,
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
                BybitApiSpec.MY_TRADES_PATH_URL
        );
    }

    @Bean
    public BybitTradeFeeSchemaLoader bybitSpotTradeFeeSchemaLoader(
            @Qualifier("bybitRestAssistant") RestAssistant restAssistant,
            @Qualifier("bybitTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        return new BybitTradeFeeSchemaLoader(restAssistant, tradingPairSymbolRegistry);
    }
}
