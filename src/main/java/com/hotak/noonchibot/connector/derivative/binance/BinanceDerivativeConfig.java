package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.*;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.throttle.ThrottlerLimitIdPreProcessor;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.core.event.ExchangeEventBus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
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
    public RestClient binanceDerivativeRestClient() {
        return RestClient.builder().baseUrl(BinanceDerivativeApiSpec.REST_BASE_URL).build();
    }

    @Bean
    public ExchangeEventBus binanceDerivativeEventBus() {
        return new ExchangeEventBus();
    }

    @Bean
    public AsyncThrottler binanceDerivativeAsyncThrottler(@Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor) {
        return new AsyncThrottlerImpl(BinanceDerivativeApiSpec.RATE_LIMITS, taskExecutor);
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
    public BinanceDerivativeOrderBookDataSource binanceDerivativeOrderBookDataSource(
            TaskScheduler taskScheduler,
            @Qualifier("binanceDerivativeWsAssistant") WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            @Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskExecutor,
            @Qualifier("binanceDerivativeTradingPairSymbolRegistry") TradingPairSymbolRegistry tradingPairSymbolRegistry,
            @Qualifier("binanceDerivativeRestAssistant") RestAssistant restAssistant
    ) {
        return new BinanceDerivativeOrderBookDataSource(
                wsAssistant,
                BinanceDerivativeApiSpec.WSS_PUBLIC_URL,
                objectMapper,
                taskExecutor,
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
}
