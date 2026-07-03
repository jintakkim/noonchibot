package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.order.OrderTradeReader;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
class TradeDataSource implements EventHandler<TradeEvent.UpdateRequested>, LifecycleAware, OrderTradeReader {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final String userAddress;
    private Subscription subscription;

    @Override
    public void onEvent(TradeEvent.UpdateRequested request) {
        if (request.exchangeOrderId() == null || request.exchangeOrderId().equals("UNKNOWN")) {
            throw new IllegalArgumentException("exchangeOrderId가 없습니다, trade를 조회할 수 없습니다.");
        }
        eventPublisher.publish(fetch(
                request.clientOrderId(),
                request.exchangeOrderId(),
                request.tradingPair()
        ));
    }

    @Override
    public TradeEvent.Received fetch(String clientOrderId, String exchangeOrderId, String tradingPair) {
        JsonNode fills = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of(
                        "type", "userFills",
                        "user", userAddress,
                        "aggregateByTime", false
                ))
                .build());

        List<TradeEvent.Fill> matched = new ArrayList<>();
        for (JsonNode fill : fills) {
            if (!exchangeOrderId.equals(String.valueOf(fill.get("oid").asLong()))) continue;
            matched.add(parseFill(fill));
        }
        return new TradeEvent.Received(
                clientOrderId,
                exchangeOrderId,
                tradingPair,
                matched
        );
    }

    private TradeEvent.Fill parseFill(JsonNode fill) {
        BigDecimal price = fill.get("px").asDecimal();
        BigDecimal size = fill.get("sz").asDecimal();
        return new TradeEvent.Fill(
                fill.get("tid").asString(),
                Instant.ofEpochMilli(fill.get("time").asLong()),
                price,
                size,
                price.multiply(size),
                new TokenAmount(fill.get("feeToken").asString(), fill.get("fee").asDecimal()),
                !fill.get("crossed").asBoolean()
        );
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(TradeEvent.UpdateRequested.class, this, ExecutionPolicy.concurrent());
    }

    @Override
    public void onShutdown() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public int phase() {
        return Phases.TRADE_DATASOURCE_SETUP;
    }
}
