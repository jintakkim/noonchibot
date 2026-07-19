package com.hotak.noonchibot.core.runtime;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class ExchangeRuntimeHealthIndicator implements HealthIndicator {
    private final ExchangeRuntimeManager runtimeManager;

    public ExchangeRuntimeHealthIndicator(ExchangeRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean failed = false;

        for (ExchangeRuntimeState state : runtimeManager.states().values()) {
            Map<String, Object> runtimeDetail = new LinkedHashMap<>();
            runtimeDetail.put("status", state.status());
            state.lastFailure().ifPresent(cause ->
                    runtimeDetail.put("failure", failureMessage(cause)));
            details.put(state.exchange().getId(), runtimeDetail);
            failed |= state.status() == ExchangeRuntimeStatus.FAILED;
        }

        Health.Builder builder = failed ? Health.down() : Health.up();
        return builder.withDetail("exchanges", details).build();
    }

    private String failureMessage(Throwable cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
