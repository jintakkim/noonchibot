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
import com.hotak.noonchibot.core.event.ExchangeEventBus;
import com.hotak.noonchibot.core.order.OrderHistoryRepository;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Configuration
public class BinanceDerivativeConfig {

    @Bean
    public ExchangeEventBus binanceDerivativeEventBus() {
        return new ExchangeEventBus();
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
            BinanceOrderBookDataSource binanceOrderBookDataSource,
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
                binanceOrderBookDataSource,
                timeSynchronizer,
                exchangeEventBus,
                restAssistant
        );
    }

    @Bean
    public OrderTracker binanceDerivativeOrderTracker(
            @Qualifier("binanceDerivativeEventBus") ExchangeEventBus eventBus,
            TradeRepository tradeRepository,
            OrderHistoryRepository orderHistoryRepository,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor
    ) {
        return new OrderTracker(
                eventBus,
                BinanceDerivativeApiSpec.PLATFORM_NAME,
                tradeRepository,
                orderHistoryRepository,
                mainExecutor,
                ioExecutor,
                eventBus
        );
    }

    @Bean
    public BinanceBalancePoller binanceDerivativeBalancePoller(
            @Qualifier("binanceUserStreamEventPublisher") BinanceUserStreamEventPublisher binanceUserStreamEventPublisher,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor,
            @Qualifier("binanceRestAssistant") RestAssistant restAssistant,
            @Qualifier("binanceEventBus") ExchangeEventBus eventBus,
            TaskScheduler taskScheduler
    ) {
        return new BinanceBalancePoller(
                binanceUserStreamEventPublisher,
                ioExecutor,
                mainExecutor,
                restAssistant,
                eventBus,
                taskScheduler
        );
    }

    @Bean
    public OrderBookTracker binanceDerivativeOrderBookTracker(
            BinanceOrderBookDataSource binanceOrderBookDataSource,
            TaskScheduler taskScheduler,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor
    ) {
        return new OrderBookTracker(binanceOrderBookDataSource, taskScheduler, mainExecutor, ioExecutor);
    }

}
