package com.hotak.noonchibot.connector.bybit;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
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

public class BybitBalancePoller implements SmartLifecycle {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final IoExecutor ioExecutor;
    private final PollScheduler pollScheduler;
    private volatile boolean running = false;

    public BybitBalancePoller(
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
                    Instant updateTime = Instant.ofEpochMilli(accountInfo.get("time").asLong());
                    Map<String, BigDecimal> totalBalances = new HashMap<>();
                    Map<String, BigDecimal> availableBalances = new HashMap<>();
                    JsonNode coinList = accountInfo.get("result").get("list").get(0).get("coin");
                    if (coinList != null && coinList.isArray()) {
                        for (JsonNode entry : coinList) {
                            String asset = entry.get("coin").asString();
                            // Bybit UNIFIED 응답에서 availableToWithdraw 는 코인이 담보로 쓰일 때 빈 문자열("")이 와서 믿을 수 없다.
                            // walletBalance(보유량) - locked(주문에 묶인 양) 로 가용 잔고를 계산한다.
                            BigDecimal walletBalance = entry.get("walletBalance").asDecimal();
                            BigDecimal lockedAmount = entry.get("locked").asDecimal();
                            totalBalances.put(asset, walletBalance);
                            availableBalances.put(asset, walletBalance.subtract(lockedAmount));
                        }
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
                        .pathUrl(BybitApiSpec.ACCOUNTS_PATH_URL)
                        .params(Map.of("accountType", "UNIFIED"))
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
