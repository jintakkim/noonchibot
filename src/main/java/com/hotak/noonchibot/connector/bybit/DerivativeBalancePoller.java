package com.hotak.noonchibot.connector.bybit;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.derivative.bybit.DerivativeApiSpec;
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
                .thenApply(response -> {
                    Instant updateTime = Instant.ofEpochMilli(response.get("time").asLong());
                    Map<String, BigDecimal> totalBalances = new HashMap<>();
                    Map<String, BigDecimal> availableBalances = new HashMap<>();
                    JsonNode coinList = response.get("result").get("list").get(0).get("coin");
                    for (JsonNode entry : coinList) {
                        String asset = entry.get("coin").asString();
                        // availableToWithdraw 는  deprecated.
                        // isolated margin: walletBalance - totalPositionIM - totalOrderIM - locked - bonus
                        BigDecimal walletBalance = entry.get("walletBalance").asDecimal();
                        BigDecimal locked = entry.get("locked").asDecimal();
                        BigDecimal orderIM = entry.get("totalOrderIM").asDecimal();
                        BigDecimal positionIM = entry.get("totalPositionIM").asDecimal();
                        BigDecimal bonus = entry.get("bonus").asDecimal();

                        totalBalances.put(asset, walletBalance);
                        availableBalances.put(asset,
                                walletBalance
                                        .subtract(locked)
                                        .subtract(orderIM)
                                        .subtract(positionIM)
                                        .subtract(bonus));
                    }
                    return new BalanceSnapshotEvent(totalBalances, availableBalances, updateTime);
                })
                .thenAccept(eventPublisher::publish);
    }

    private CompletableFuture<JsonNode> fetchBalances() {
        return ioExecutor.submitCompletable(() -> restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(DerivativeApiSpec.ACCOUNTS_PATH_URL)
                        .params(Map.of("accountType", "UNIFIED"))
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
