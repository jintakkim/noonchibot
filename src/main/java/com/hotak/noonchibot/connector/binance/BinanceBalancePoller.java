package com.hotak.noonchibot.connector.binance;

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

public class BinanceBalancePoller implements SmartLifecycle {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final IoExecutor ioExecutor;
    private final PollScheduler pollScheduler;
    private volatile boolean running = false;

    public BinanceBalancePoller(
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
        fetchAccountInfo()
                .thenApply(accountInfo -> {
                    Instant updateTime = Instant.ofEpochMilli(accountInfo.get("updateTime").asLong());

                    Map<String, BigDecimal> totalBalances = new HashMap<>();
                    Map<String, BigDecimal> availableBalances = new HashMap<>();
                    for (JsonNode entry : accountInfo.get("balances")) {
                        String asset = entry.get("asset").asString();
                        BigDecimal free = entry.get("free").asDecimal();
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
                        .pathUrl(BinanceApiSpec.ACCOUNTS_PATH_URL)
                        .build()
        ));
    }

    @Override
    public void start() {
        pollScheduler.start(this::pollData);
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
