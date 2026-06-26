package com.hotak.noonchibot.connector.binance.spot;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.balance.AssetState;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.balance.BalanceEvent;
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

    @VisibleForTesting
    void poll() {
        eventPublisher.publish(parseSnapshot(fetchBalances()));
    }

    private BalanceEvent.SnapshotReceived parseSnapshot(JsonNode account) {
        Instant timestamp = Instant.ofEpochMilli(account.path("updateTime").asLong());
        Map<String, AssetState> assets = new HashMap<>();
        for (JsonNode balance : account.get("balances")) {
            String asset = balance.get("asset").asString();
            BigDecimal available = balance.get("free").asDecimal();
            BigDecimal locked = balance.get("locked").asDecimal();
            assets.put(asset, new AssetState(available.add(locked), available, timestamp));
        }
        return new BalanceEvent.SnapshotReceived(assets, timestamp);
    }

    private JsonNode fetchBalances() {
        return restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(ApiSpec.ACCOUNTS_PATH_URL)
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
