package com.hotak.noonchibot.core.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CircuitBreakerEventHandler {
    private final ExchangeHealthRegistry healthRegistry;
    private final Set<String> subscribedCircuitNames = ConcurrentHashMap.newKeySet();

    public CircuitBreakerEventHandler(
            CircuitBreakerRegistry circuitBreakerRegistry,
            ExchangeHealthRegistry healthRegistry
    ) {
        this.healthRegistry = healthRegistry;
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::subscribe);
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> subscribe(event.getAddedEntry()));
    }

    private void subscribe(CircuitBreaker circuitBreaker) {
        if (!subscribedCircuitNames.add(circuitBreaker.getName())) {
            return;
        }

        healthRegistry.updateState(circuitBreaker.getName(), circuitBreaker.getState());
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    CircuitBreaker.State toState = event.getStateTransition().getToState();
                    healthRegistry.updateState(circuitBreaker.getName(), toState);
                    log.warn("circuit breaker state changed. name={}, transition={}",
                            circuitBreaker.getName(), event.getStateTransition());
                })
                .onCallNotPermitted(event -> {
                    healthRegistry.markCallNotPermitted(circuitBreaker.getName());
                    log.warn("circuit breaker rejected call. name={}", circuitBreaker.getName());
                });
    }
}
