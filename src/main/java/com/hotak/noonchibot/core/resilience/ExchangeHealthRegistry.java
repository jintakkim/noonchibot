package com.hotak.noonchibot.core.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ExchangeHealthRegistry {
    private final Map<String, CircuitHealth> healthByCircuitName = new ConcurrentHashMap<>();

    public void updateState(String circuitName, CircuitBreaker.State state) {
        healthByCircuitName.compute(circuitName, (name, current) -> new CircuitHealth(
                name,
                state,
                Instant.now(),
                current == null ? 0 : current.callNotPermittedCount()
        ));
    }

    public void markCallNotPermitted(String circuitName) {
        healthByCircuitName.compute(circuitName, (name, current) -> new CircuitHealth(
                name,
                current == null ? CircuitBreaker.State.OPEN : current.state(),
                Instant.now(),
                current == null ? 1 : current.callNotPermittedCount() + 1
        ));
    }

    public Optional<CircuitHealth> find(String circuitName) {
        return Optional.ofNullable(healthByCircuitName.get(circuitName));
    }

    public boolean isAvailable(String circuitName) {
        return find(circuitName)
                .map(CircuitHealth::isAvailable)
                .orElse(true);
    }

    public record CircuitHealth(
            String circuitName,
            CircuitBreaker.State state,
            Instant updatedAt,
            long callNotPermittedCount
    ) {
        public boolean isAvailable() {
            return state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN;
        }
    }
}
