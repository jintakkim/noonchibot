package com.hotak.noonchibot.connector.derivative.bybit;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.BalanceSnapshotEvent;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BybitDerivativeBalancePoller implements SmartLifecycle {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final IoExecutor ioExecutor;
    private final MainExecutor mainExecutor;
    private final PollScheduler pollScheduler;
    private volatile boolean running = false;

    public BybitDerivativeBalancePoller(
            WebsocketStatus websocketStatus,
            IoExecutor ioExecutor,
            MainExecutor mainExecutor,
            RestAssistant restAssistant,
            ExchangeEventPublisher eventPublisher,
            TaskScheduler taskScheduler
    ) {
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.pollScheduler = new PollScheduler(websocketStatus, taskScheduler);
    }

    @VisibleForTesting
    void pollData() {
        fetchBalances()
                .thenApply(response -> {
                    Map<String, BigDecimal> totalBalances = new HashMap<>();
                    Map<String, BigDecimal> availableBalances = new HashMap<>();

                    JsonNode list = response.get("result").get("list");
                    if (list == null || list.isEmpty()) {
                        return new BalanceSnapshotEvent(totalBalances, availableBalances, Instant.now());
                    }

                    JsonNode coins = list.get(0).get("coin");
                    for (JsonNode entry : coins) {
                        String asset = entry.get("coin").asString();
                        totalBalances.put(asset, entry.get("walletBalance").asDecimal());
                        availableBalances.put(asset, entry.get("availableToWithdraw").asDecimal());
                    }
                    return new BalanceSnapshotEvent(totalBalances, availableBalances, Instant.now());
                })
                .thenAcceptAsync(eventPublisher::publish, mainExecutor)
                .exceptionally(ex -> {
                    return null;
                });
    }

    private CompletableFuture<JsonNode> fetchBalances() {
        return ioExecutor.submitCompletable(() -> restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(BybitDerivativeApiSpec.ACCOUNTS_PATH_URL)
                        .params(Map.of("accountType", "UNIFIED"))
                        .build()
        ));
    }

    @Override
    public void start() {
        pollScheduler.start(() -> mainExecutor.execute(this::pollData));
        running = true;
    }

    @Override
    public void stop() {
        pollScheduler.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
