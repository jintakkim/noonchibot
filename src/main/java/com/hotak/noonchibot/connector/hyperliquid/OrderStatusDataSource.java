package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderStatusReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class OrderStatusDataSource implements EventHandler<OrderEvent.StatusUpdateRequested>, LifecycleAware, OrderStatusReader {
    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final String userAddress;
    private Subscription subscription;

    @Override
    public void onEvent(OrderEvent.StatusUpdateRequested event) {
        try {
            eventPublisher.publish(fetch(event.tradingPair(), event.clientOrderId()));
        } catch (IllegalStateException exception) {
            log.warn(
                    "Order not found on exchange: tradingPair={}, cloid={}",
                    event.tradingPair(),
                    event.clientOrderId()
            );
        }
    }

    @Override
    public OrderEvent.StatusReceived fetch(String tradingPair, String clientOrderId) {
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of(
                        "type", "orderStatus",
                        "user", userAddress,
                        "oid", clientOrderId
                ))
                .build());

        if ("unknownOid".equals(response.get("status").asString())) {
            throw new IllegalStateException("order not found on exchange: " + clientOrderId);
        }
        JsonNode orderWrapper = response.get("order");
        JsonNode orderNode = orderWrapper.get("order");
        OrderState state = DerivativeApiSpec.ORDER_STATE.get(orderWrapper.get("status").asString());
        return new OrderEvent.StatusReceived(
                tradingPair,
                clientOrderId,
                String.valueOf(orderNode.get("oid").asLong()),
                state,
                Instant.ofEpochMilli(orderWrapper.get("statusTimestamp").asLong())
        );
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(OrderEvent.StatusUpdateRequested.class, this, ExecutionPolicy.concurrent());
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
        return Phases.ORDER_STATUS_DATASOURCE_SETUP;
    }
}
