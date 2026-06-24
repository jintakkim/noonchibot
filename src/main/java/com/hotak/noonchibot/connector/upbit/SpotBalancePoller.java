package com.hotak.noonchibot.connector.upbit;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class SpotBalancePoller implements LifecycleComponent {
    private final RestAssistantImpl restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final IoExecutor ioExecutor;
    private final PollScheduler pollScheduler;

    public SpotBalancePoller(
            WebsocketStatus websocketStatus,
            IoExecutor ioExecutor,
            RestAssistantImpl restAssistant,
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
        fetchAccountInfo()
                .thenApply(accountInfo -> {
                    // 업비트는 updateTime을 제공하지 않는다
                    Instant updateTime = Instant.now();

                    Map<String, BigDecimal> totalBalances = new HashMap<>();
                    Map<String, BigDecimal> availableBalances = new HashMap<>();

                    for (JsonNode entry : accountInfo) {
                        String asset = entry.get("currency").asString();
                        BigDecimal free = entry.get("balance").asDecimal();
                        BigDecimal locked = entry.get("locked").asDecimal();
                        totalBalances.put(asset, free.add(locked));
                        availableBalances.put(asset, free);
                    }
                    return new BalanceSnapshotEvent(totalBalances, availableBalances, updateTime);
                })
                .thenAccept(eventPublisher::publish);
    }

    private CompletableFuture<JsonNode> fetchAccountInfo() {
        return ioExecutor.submitCompletable(() -> restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(SpotApiSpec.ACCOUNTS_PATH_URL)
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
