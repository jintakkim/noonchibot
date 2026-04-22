package com.hotak.noonchibot.connector.binance;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.BalanceSnapshotEvent;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class DerivativeBalancePoller implements LifecycleComponent {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final IoExecutor ioExecutor;
    private final PollScheduler pollScheduler;

    public DerivativeBalancePoller(
            WebsocketStatus websocketStatus,
            IoExecutor ioExecutor,
            RestAssistant restAssistant,
            ExchangeEventPublisher eventPublisher,
            TaskScheduler taskScheduler
    ) {
        this.ioExecutor = ioExecutor;
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.pollScheduler = new PollScheduler(websocketStatus, taskScheduler);
    }

    @VisibleForTesting
    void pollData() {
        fetchBalances()
                .thenApply(balances -> {
                    Map<String, BigDecimal> totalBalances = new HashMap<>();
                    Map<String, BigDecimal> availableBalances = new HashMap<>();
                    for (JsonNode entry : balances) {
                        String asset = entry.get("asset").asString();
                        totalBalances.put(asset, entry.get("balance").asDecimal());
                        availableBalances.put(asset, entry.get("availableBalance").asDecimal());
                    }
                    return new BalanceSnapshotEvent(totalBalances, availableBalances, Instant.now());
                })
                .thenAccept(eventPublisher::publish);
    }

    private CompletableFuture<JsonNode> fetchBalances() {
        return ioExecutor.submitCompletable(() -> restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(DerivativeApiSpec.BALANCE_PATH_URL)
                        .build()
        ));
    }

    @Override
    public void start() {
        pollScheduler.start(this::pollData);
    }

    @Override
    public void shutdown() {
        pollScheduler.stop();
    }
}