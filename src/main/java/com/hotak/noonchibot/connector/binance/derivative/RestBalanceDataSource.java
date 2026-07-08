package com.hotak.noonchibot.connector.binance.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

class RestBalanceDataSource implements LifecycleAware {
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final TaskScheduler taskScheduler;
    private volatile ScheduledFuture<?> task;


    public RestBalanceDataSource(
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler
    ) {
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.taskScheduler = taskScheduler;
    }

    public RestBalanceDataSource(
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.balance(Exchange.BINANCE_DERIVATIVE))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                eventPublisher,
                taskScheduler
        );
    }

    @VisibleForTesting
    void poll() {
        JsonNode res = fetchBalances();
        BalanceEvent.SnapshotReceived snapshot = parseSnapshot(res);
        eventPublisher.publish(snapshot);
    }

    private BalanceEvent.SnapshotReceived parseSnapshot(JsonNode account) {
        Map<String, AssetState> assets = new HashMap<>();
        for (JsonNode asset : account.get("assets")) {
            String name = asset.get("asset").asString();
            BigDecimal marginBalance = asset.get("marginBalance").asDecimal();
            BigDecimal availableBalance = asset.get("availableBalance").asDecimal();
            Instant timestamp = Instant.ofEpochMilli(asset.get("updateTime").asLong());
            assets.put(name, new AssetState(marginBalance, availableBalance, timestamp));
        }
        Instant timestamp = assets.values().stream()
                .map(AssetState::timestamp)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(Instant.now());
        return new BalanceEvent.SnapshotReceived(assets, timestamp);
    }


    private JsonNode fetchBalances() {
        return restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(ApiSpec.ACCOUNT_PATH_URL)
                        .build()
        );
    }

    @Override
    public int phase() {
        return Phases.BALANCE_SETUP;
    }

    @Override
    public void onStart() {
        poll();
        task = taskScheduler.scheduleAtFixedRate(this::poll, POLL_INTERVAL);
    }

    @Override
    public void onShutdown() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
    }
}
