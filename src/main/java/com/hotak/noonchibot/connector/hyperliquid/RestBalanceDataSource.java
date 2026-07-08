package com.hotak.noonchibot.connector.hyperliquid;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.DefaultExchangeErrorClassifier;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

class RestBalanceDataSource implements LifecycleAware {
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final String COLLATERAL_ASSET = "USDC";

    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final TaskScheduler taskScheduler;
    private final String userAddress;
    private volatile ScheduledFuture<?> task;

    public RestBalanceDataSource(
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            String userAddress
    ) {
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.taskScheduler = taskScheduler;
        this.userAddress = userAddress;
    }

    public RestBalanceDataSource(
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            TaskScheduler taskScheduler,
            String userAddress,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.balance(Exchange.HYPERLIQUID_DERIVATIVE))
                        .errorClassifier(new DefaultExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                eventPublisher,
                taskScheduler,
                userAddress
        );
    }

    @VisibleForTesting
    void poll() {
        Instant timestamp = Instant.now();
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of("type", "clearinghouseState", "user", userAddress))
                .build());
        JsonNode marginSummary = response.get("marginSummary");
        AssetState state = new AssetState(
                marginSummary.get("accountValue").asDecimal(),
                response.get("withdrawable").asDecimal(),
                timestamp
        );
        eventPublisher.publish(new BalanceEvent.SnapshotReceived(Map.of(COLLATERAL_ASSET, state), timestamp));
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

    @Override
    public int phase() {
        return Phases.BALANCE_SETUP;
    }
}
