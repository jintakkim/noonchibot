package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.StructuredOrderIdGenerator;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.connector.binance.*;
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
public class BinanceDerivativeConfig {

    @Bean
    public ExchangeEventBus binanceDerivativeEventBus(MainExecutor mainExecutor) {
        return new ExchangeEventBus(mainExecutor);
    }

    @Bean
    public RestClient binanceDerivativeRestClient() {
        return RestClient.builder().baseUrl(BinanceDerivativeApiSpec.REST_BASE_URL).build();
    }

    @Bean
    public BinanceAuthenticator binanceDerivativeAuthenticator(
            BinanceConfig.BinanceProperties binanceProperties,
            @Qualifier("binanceDerivativeTimeSynchronizer")
            TimeSynchronizer timeSynchronizer,
            ObjectMapper objectMapper
    ) {
        return new BinanceAuthenticator(binanceProperties.apiKey(), binanceProperties.secretKey(), timeSynchronizer, objectMapper);
    }

    @Bean
    public RestAssistant binanceDerivativeRestAssistant(
            @Qualifier("binanceDerivativeRestClient")
            RestClient restClient,
            @Qualifier("binanceDerivativeAsyncThrottler")
            AsyncThrottler asyncThrottler,
            BinanceAuthenticator binanceAuthenticator,
            ObjectMapper objectMapper
    ) {
        return new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor()),
                List.of(),
                binanceAuthenticator,
                asyncThrottler,
                objectMapper
        );
    }

    @Bean
    public WsAssistant binanceDerivativeWsAssistant(
            WebSocketClient webSocketClient,
            ObjectMapper objectMapper,
            @Qualifier("binanceDerivativeAuthenticator")
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
    public TradingPairSymbolRegistry binanceDerivativeTradingPairSymbolRegistry() {
        return new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
    }

    @Bean
    public AsyncThrottler binanceDerivativeAsyncThrottler(
            IoExecutor ioExecutor
    ) {
        return new AsyncThrottlerImpl(BinanceDerivativeApiSpec.RATE_LIMITS, ioExecutor);
    }

    @Bean
    public TimeSynchronizer binanceDerivativeTimeSynchronizer(
            @Qualifier("binanceDerivativeRestClient") RestClient restClient,
            @Qualifier("binanceDerivativeAsyncThrottler")
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
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(new BinanceServerTimeProvider(publicRestAssistant, BinanceDerivativeApiSpec.SERVER_TIME_PATH_URL), taskScheduler);
        timeSynchronizer.scheduleUpdate();
        return timeSynchronizer;
    }

    @Bean
    public BinanceDerivativeOrderBookDataSource binanceDerivativeOrderBookDataSource(
            TaskScheduler taskScheduler,
            @Qualifier("binanceDerivativeWsAssistant") WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant
    ) {
        return new BinanceDerivativeOrderBookDataSource(
                wsAssistant,
                BinanceDerivativeApiSpec.WSS_PUBLIC_URL,
                objectMapper,
                ioExecutor,
                taskScheduler,
                tradingPairSymbolRegistry,
                restAssistant
        );
    }

    @Bean
    public BinanceTradingRuleRegistry binanceDerivativeTradingRuleRegistry(
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper
    ) {
        return new BinanceTradingRuleRegistry(
                restAssistant,
                new BinanceDerivativeTradingRuleParser(tradingPairSymbolRegistry),
                taskScheduler,
                BinanceDerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL,
                tradingPairSymbolRegistry,
                BinanceDerivativeApiSpec.EXCHANGE_INFO_PATH_URL,
                objectMapper
        );
    }

    @Bean
    public BinanceDerivativeOrderExecutor binanceDerivativeOrderExecutor(
            BinanceDerivativeOrderBookDataSource binanceDerivativeOrderBookDataSource,
            @Qualifier("binanceDerivativeOrderTracker") OrderTracker orderTracker,
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus exchangeEventBus,
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("binanceDerivativeTimeSynchronizer") TimeSynchronizer timeSynchronizer,
            @Qualifier("binanceDerivativeTradingRuleRegistry") TradingRuleRegistry tradingRuleRegistry

    ) {
        return new BinanceDerivativeOrderExecutor(
                new StructuredOrderIdGenerator(),
                orderTracker,
                tradingRuleRegistry,
                tradingPairSymbolRegistry,
                binanceDerivativeOrderBookDataSource,
                timeSynchronizer,
                exchangeEventBus,
                restAssistant
        );
    }

    @Bean
    public BinanceDerivativeBalancePoller binanceDerivativeBalancePoller(
            BinanceDerivativeUserStreamEventPublisher binanceDerivativeUserStreamEventPublisher,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor,
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus,
            TaskScheduler taskScheduler
    ) {
        return new BinanceDerivativeBalancePoller(
                binanceDerivativeUserStreamEventPublisher,
                ioExecutor,
                mainExecutor,
                restAssistant,
                eventBus,
                taskScheduler
        );
    }

    @Bean
    public OrderBookTracker binanceDerivativeOrderBookTracker(
            BinanceDerivativeOrderBookDataSource binanceDerivativeOrderBookDataSource,
            TaskScheduler taskScheduler,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor
    ) {
        return new OrderBookTracker(binanceDerivativeOrderBookDataSource, taskScheduler, mainExecutor, ioExecutor);
    }

    @Bean
    public AccountBalanceTracker binanceDerivativeAccountBalanceTracker(
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus
    ) {
        return new AccountBalanceTracker(BinanceDerivativeApiSpec.PLATFORM_NAME, eventBus);
    }

    @Bean
    public OrderTracker binanceDerivativeOrderTracker(
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository,
            IoExecutor ioExecutor
    ) {
        return new OrderTracker(
                eventBus,
                BinanceDerivativeApiSpec.PLATFORM_NAME,
                tradeRepository,
                orderHistoryRepository,
                ioExecutor,
                eventBus
        );
    }

    @Bean
    @DependsOn({"binanceDerivativeAccountBalanceTracker", "binanceDerivativeOrderTracker"})
    public BinanceDerivativeUserStreamEventPublisher binanceDerivativeUserStreamEventPublisher(
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceDerivativeWsAssistant") WsAssistant wsAssistant,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            IoExecutor ioExecutor
    ) {
        return new BinanceDerivativeUserStreamEventPublisher(
                restAssistant,
                wsAssistant,
                taskScheduler,
                objectMapper,
                tradingPairSymbolRegistry,
                eventBus,
                ioExecutor
        );
    }

    @Bean
    public BinanceOrderStatusPoller binanceDerivativeOrderStatusPoller(
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus,
            @Qualifier("binanceDerivativeOrderTracker") OrderTracker orderTracker,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            BinanceDerivativeUserStreamEventPublisher userStreamEventPublisher,
            TaskScheduler taskScheduler
    ) {
        return new BinanceOrderStatusPoller(
                restAssistant,
                eventBus,
                orderTracker,
                mainExecutor,
                ioExecutor,
                tradingPairSymbolRegistry,
                userStreamEventPublisher,
                taskScheduler,
                BinanceDerivativeApiSpec.ORDER_PATH_URL
        );
    }

    @Bean
    public BinanceTradePoller binanceDerivativeTradePoller(
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            TaskScheduler taskScheduler,
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus,
            @Qualifier("binanceDerivativeOrderTracker") OrderTracker orderTracker,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            BinanceDerivativeUserStreamEventPublisher userStreamEventPublisher,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor
    ) {
        return new BinanceTradePoller(
                userStreamEventPublisher,
                eventBus,
                restAssistant,
                taskScheduler,
                orderTracker,
                tradingPairSymbolRegistry,
                ioExecutor,
                mainExecutor,
                BinanceDerivativeApiSpec.TRADE_PATH_URL
        );
    }

    @Bean
    public BinanceDerivativeTradeFeeSchemaLoader binanceDerivativeTradeFeeSchemaLoader(
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        return new BinanceDerivativeTradeFeeSchemaLoader(restAssistant, tradingPairSymbolRegistry);
    }


}
