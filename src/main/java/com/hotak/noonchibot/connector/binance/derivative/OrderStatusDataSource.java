package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

@Slf4j
record OrderStatusDataSource(
        TradingPairSymbolRegistry tradingPairSymbolRegistry,
        RestAssistant restAssistant,
        EventPublisher eventPublisher
) implements EventHandler<OrderEvent.StatusUpdateRequested> {

    @Override
    public void onEvent(OrderEvent.StatusUpdateRequested event) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(event.tradingPair());
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", event.clientOrderId()))
                .authRequired(true)
                .build();

        JsonNode updatedOrder = restAssistant.executeRequestAndGetJsonBody(request);
        OrderState newState = ApiSpec.ORDER_STATE.get(updatedOrder.get("status").asString());
        eventPublisher.publish(new OrderEvent.StatusReceived(
                event.tradingPair(),
                event.clientOrderId(),
                updatedOrder.get("orderId").asString(),
                newState,
                Instant.ofEpochMilli(updatedOrder.get("updateTime").asLong())
        ));
    }
}
