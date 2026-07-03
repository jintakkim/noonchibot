package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
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

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    @Override
    public void onEvent(OrderEvent.StatusUpdateRequested event) {
        eventPublisher.publish(fetch(event.tradingPair(), event.clientOrderId()));
    }

    @Override
    public OrderEvent.StatusReceived fetch(String tradingPair, String clientOrderId) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", clientOrderId))
                .authRequired(true)
                .build();

        JsonNode updatedOrder = restAssistant.executeRequestAndGetJsonBody(request);
        OrderState newState = ApiSpec.ORDER_STATE.get(updatedOrder.get("status").asString());
        return new OrderEvent.StatusReceived(
                tradingPair,
                clientOrderId,
                updatedOrder.get("orderId").asString(),
                newState,
                Instant.ofEpochMilli(updatedOrder.get("updateTime").asLong())
        );
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                OrderEvent.StatusUpdateRequested.class,
                this,
                ExecutionPolicy.concurrent()
        );

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
