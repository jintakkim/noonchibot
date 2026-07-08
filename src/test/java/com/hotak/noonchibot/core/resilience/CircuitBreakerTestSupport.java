package com.hotak.noonchibot.core.resilience;

import com.hotak.noonchibot.connector.ExchangeRejectedException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;

public final class CircuitBreakerTestSupport {
    private CircuitBreakerTestSupport() {
    }

    public static CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .ignoreExceptions(ExchangeRejectedException.class)
                .recordExceptions(ExchangeTransientException.class)
                .build();
        return CircuitBreakerRegistry.of(config);
    }
}
