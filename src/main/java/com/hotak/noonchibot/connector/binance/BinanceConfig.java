package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.throttle.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
@EnableConfigurationProperties(BinanceConfig.BinanceProperties.class)
public class BinanceConfig {

    @ConfigurationProperties(prefix = "binance")
    public record BinanceProperties(String apiKey, String secretKey) {}

    @Bean
    public RestClient binanceRestClient() {
        return RestClient.builder().baseUrl(BinanceApiSpec.REST_BASE_URL).build();
    }

    @Bean
    public RestAssistant binancePublicRestAssistant(
            @Qualifier("binanceRestClient")
            RestClient restClient,
            @Qualifier("binanceAsyncThrottler")
            AsyncThrottler asyncThrottler,
            ObjectMapper objectMapper
    ) {
        return new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor(), new BinanceApiVersionPrefixPreProcessor()),
                List.of(),
                null,
                asyncThrottler,
                objectMapper
        );
    }

    @Bean
    public RestAssistant binanceRestAssistant(
            @Qualifier("binanceRestClient")
            RestClient restClient,
            @Qualifier("binanceAsyncThrottler")
            AsyncThrottler asyncThrottler,
            BinanceProperties binanceProperties,
            ObjectMapper objectMapper,
            @Qualifier("binanceTimeSynchronizer")
            TimeSynchronizer timeSynchronizer
    ) {
        return new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor(), new BinanceApiVersionPrefixPreProcessor()),
                List.of(),
                new BinanceAuthenticator(binanceProperties.apiKey, binanceProperties.secretKey, timeSynchronizer, objectMapper),
                asyncThrottler,
                objectMapper
        );
    }

    @Bean
    public AsyncThrottler binanceAsyncThrottler(@Qualifier("virtualThreadAsyncTaskExecutor") AsyncTaskExecutor taskScheduler) {
        return new AsyncThrottlerImpl(BinanceApiSpec.RATE_LIMITS, taskScheduler);
    }

    @Bean
    public TimeSynchronizer binanceTimeSynchronizer(
            RestClient restClient,
            @Qualifier("binanceAsyncThrottler")
            AsyncThrottler asyncThrottler,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler
    ) {
        RestAssistant publicRestAssistant = new RestAssistant(
                restClient,
                List.of(new ThrottlerLimitIdPreProcessor(), new BinanceApiVersionPrefixPreProcessor()),
                List.of(),
                null,
                asyncThrottler,
                objectMapper
        );
        TimeSynchronizer timeSynchronizer = new TimeSynchronizer(new BinanceServerTimeProvider(publicRestAssistant), taskScheduler);
        timeSynchronizer.scheduleUpdate();
        return timeSynchronizer;
    }
}
