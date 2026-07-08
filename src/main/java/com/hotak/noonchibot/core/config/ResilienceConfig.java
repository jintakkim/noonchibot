package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.connector.ExchangeProtocolException;
import com.hotak.noonchibot.connector.ExchangeRejectedException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.OrderValidationException;
import com.hotak.noonchibot.core.resilience.CircuitBreakerEventHandler;
import com.hotak.noonchibot.core.resilience.ExchangeHealthRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                .ignoreExceptions(
                        ExchangeRejectedException.class,
                        OrderValidationException.class
                )
                .recordException(this::shouldRecordException)
                .build();

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    @Bean
    public ExchangeHealthRegistry exchangeHealthRegistry() {
        return new ExchangeHealthRegistry();
    }

    @Bean
    public CircuitBreakerEventHandler circuitBreakerEventHandler(
            CircuitBreakerRegistry circuitBreakerRegistry,
            ExchangeHealthRegistry exchangeHealthRegistry
    ) {
        return new CircuitBreakerEventHandler(circuitBreakerRegistry, exchangeHealthRegistry);
    }

    private boolean shouldRecordException(Throwable throwable) {
        return throwable instanceof ExchangeTransientException
                || throwable instanceof ExchangeProtocolException
                || throwable instanceof RestClientException
                || throwable instanceof CallNotPermittedException
                || throwable instanceof IOException
                || throwable instanceof TimeoutException
                || throwable instanceof IllegalStateException
                || throwable instanceof NullPointerException;
    }
}
