package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.AbstractExchangeDataPoller;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.OrderStreamStatus;
import com.hotak.noonchibot.core.event.BalanceSnapshotEvent;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class BinanceBalancePoller extends AbstractExchangeDataPoller {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;

    public BinanceBalancePoller(
            OrderStreamStatus orderStreamStatus,
            AsyncTaskExecutor taskExecutor,
            RestAssistant restAssistant,
            ExchangeEventPublisher eventPublisher
    ) {
        super(orderStreamStatus, taskExecutor);
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected void pollData() {
        JsonNode accountInfo = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(BinanceApiSpec.ACCOUNTS_PATH_URL)
                        .build()
        );
        Instant updateTime = Instant.ofEpochMilli(accountInfo.get("updateTime").asLong());
        Map<String, BigDecimal> free = new HashMap<>();
        Map<String, BigDecimal> locked = new HashMap<>();
        for (JsonNode entry : accountInfo.get("balances")) {
            String asset = entry.get("asset").asString();
            free.put(asset, entry.get("free").asDecimal());
            locked.put(asset, entry.get("locked").asDecimal());
        }
        eventPublisher.publish(new BalanceSnapshotEvent(free, locked, updateTime));
    }
}
