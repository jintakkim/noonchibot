package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.throttle.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.event.ExchangeEventBus;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(BinanceConfig.BinanceProperties.class)
public class BinanceConfig {

    @ConfigurationProperties(prefix = "binance")
    public record BinanceProperties(
            String apiKey,
            String secretKey
    ) {}

    @Bean
    public ExchangeEventBus binanceEventBus() {
        return new ExchangeEventBus();
    }

    @Bean
    public RestClient binanceRestClient() {
        return RestClient.builder().baseUrl(BinanceApiSpec.REST_BASE_URL).build();
    }

    @Bean
    public BinanceAuthenticator binanceAuthenticator(
            BinanceProperties binanceProperties,
            @Qualifier("binanceTimeSynchronizer")
            TimeSynchronizer timeSynchronizer,
            ObjectMapper objectMapper
    ) {
        return new BinanceAuthenticator(binanceProperties.apiKey, binanceProperties.secretKey, timeSynchronizer, objectMapper);
    }

    @Bean
    public RestAssistant binanceRestAssistant(
            @Qualifier("binanceRestClient")
            RestClient restClient,
            @Qualifier("binanceAsyncThrottler")
            AsyncThrottler asyncThrottler,
            BinanceAuthenticator binanceAuthenticator,
            ObjectMapper objectMapper
    ) {
        return new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor(), new BinanceApiVersionPrefixRestPreProcessor()),
                List.of(),
                binanceAuthenticator,
                asyncThrottler,
                objectMapper
        );
    }

    @Bean
    public WsAssistant binanceWsAssistant(
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            @Qualifier("binanceAuthenticator")
            BinanceAuthenticator binanceAuthenticator
    ) {
        return new WsAssistant(
                webSocketClient,
                new WebSocketHttpHeaders(),
                List.of(),
                List.of(),
                objectMapper,
                binanceAuthenticator
        );
    }

    @Bean
    public TradingPairSymbolRegistry binanceTradingPairSymbolRegistry() {
        return new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
    }

    @Bean
    public AsyncThrottler binanceAsyncThrottler(@Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor) {
        return new AsyncThrottlerImpl(BinanceApiSpec.RATE_LIMITS, taskExecutor);
    }

    @Bean
    public TimeSynchronizer binanceTimeSynchronizer(
            @Qualifier("binanceRestClient")
            RestClient restClient,
            @Qualifier("binanceAsyncThrottler")
            AsyncThrottler asyncThrottler,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler
    ) {
        RestAssistant publicRestAssistant = new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor(), new BinanceApiVersionPrefixRestPreProcessor()),
                List.of(),
                null,
                asyncThrottler,
                objectMapper
        );
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(new BinanceServerTimeProvider(publicRestAssistant), taskScheduler);
        timeSynchronizer.scheduleUpdate();
        return timeSynchronizer;
    }


    @Bean
    public BinanceOrderBookDataSource binanceOrderBookDataSource(
           @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
           @Qualifier("binanceWsAssistant") WsAssistant wsAssistant,
           @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
           ObjectMapper objectMapper,
           TaskScheduler taskScheduler
    ) {
        return new BinanceOrderBookDataSource(
                taskScheduler,
                wsAssistant,
                BinanceApiSpec.WSS_URL,
                objectMapper,
                "binance-order-book-update-thread",
                tradingPairSymbolRegistry,
                restAssistant
        );
    }

    @Bean
    public BinanceTradingRuleRegistry binanceTradingRuleRegistry(
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper
    ) {
        return new BinanceTradingRuleRegistry(
                restAssistant,
                new BinanceTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                BinanceApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                BinanceApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper
        );
    }

    @Bean
    public BinanceOrderExecutor binanceOrderExecutor(
            BinanceOrderBookDataSource binanceOrderBookDataSource,
            @Qualifier("binanceOrderTracker") OrderTracker orderTracker,
            @Qualifier("binanceEventBus") ExchangeEventBus exchangeEventBus,
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("binanceTimeSynchronizer") TimeSynchronizer timeSynchronizer,
            @Qualifier("binanceTradingRuleRegistry") TradingRuleRegistry tradingRuleRegistry

            ) {
        return new BinanceOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                tradingRuleRegistry,
                tradingPairSymbolRegistry,
                binanceOrderBookDataSource,
                timeSynchronizer,
                exchangeEventBus,
                restAssistant
        );
    }

    @Bean
    public BinanceBalancePoller binanceBalancePoller(
            @Qualifier("binanceUserStreamEventPublisher") BinanceUserStreamEventPublisher binanceUserStreamEventPublisher,
            @Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor,
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceEventBus") ExchangeEventBus eventBus
    ) {
        return new BinanceBalancePoller(binanceUserStreamEventPublisher, taskExecutor, restAssistant, eventBus);
    }

    @Bean
    public OrderBookTracker binanceOrderBookTracker(
            BinanceOrderBookDataSource binanceOrderBookDataSource,
            TaskScheduler taskScheduler,
            @Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor
    ) {
        return new OrderBookTracker(binanceOrderBookDataSource, taskScheduler, taskExecutor);
    }


    @Bean
    public AccountBalanceTracker binanceAccountBalanceTracker(@Qualifier("binanceEventBus") ExchangeEventBus eventBus) {
        return new AccountBalanceTracker(BinanceApiSpec.PLATFORM_NAME, eventBus);
    }

    @Bean
    public OrderTracker binanceOrderTracker(@Qualifier("binanceEventBus") ExchangeEventBus eventBus) {
        return new OrderTracker(eventBus, eventBus);
    }

    @Bean
    @DependsOn({"binanceAccountBalanceTracker", "binanceOrderTracker"})
    public BinanceUserStreamEventPublisher binanceUserStreamEventPublisher(
            @Qualifier("binanceWsAssistant") WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            BinanceAuthenticator binanceAuthenticator,
            @Qualifier("binanceEventBus") ExchangeEventBus eventBus,
            @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        return new BinanceUserStreamEventPublisher(
                wsAssistant,
                objectMapper,
                binanceAuthenticator,
                List.of(new BinanceExecutionReportParser(tradingPairSymbolRegistry), new BinanceBalanceUpdateParser()),
                eventBus
        );
    }

    @Bean
    public BinanceOrderStatusPoller binanceOrderStatusPoller(
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceOrderTracker") OrderTracker orderTracker,
            @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("binanceUserStreamEventPublisher") BinanceUserStreamEventPublisher userStreamEventPublisher,
            @Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor

    ) {
        return new BinanceOrderStatusPoller(restAssistant, orderTracker, tradingPairSymbolRegistry, userStreamEventPublisher, taskExecutor);
    }

    @Bean
    public BinanceTradePoller binanceTradePoller(
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceOrderTracker") OrderTracker orderTracker,
            @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("binanceUserStreamEventPublisher") BinanceUserStreamEventPublisher userStreamEventPublisher,
            @Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor
    ) {
        return new BinanceTradePoller(userStreamEventPublisher, taskExecutor, restAssistant, orderTracker, tradingPairSymbolRegistry);
    }

    @Bean
    public BinanceSpotTradeFeeSchemaLoader binanceSpotTradeFeeSchemaLoader(
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        return new BinanceSpotTradeFeeSchemaLoader(restAssistant, tradingPairSymbolRegistry);
    }
}
