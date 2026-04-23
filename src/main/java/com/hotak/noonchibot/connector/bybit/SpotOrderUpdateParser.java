package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.ExchangeEvent;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
class SpotOrderUpdateParser implements UserStreamEventParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public boolean canParse(JsonNode msg) {
        return "order".equals(msg.path("topic").asString());
    }

    @Override
    public List<ExchangeEvent> parse(JsonNode msg) {
        List<ExchangeEvent> events = new ArrayList<>();
        // Bybit order 스트림은 data 배열에 여러 주문이 올 수 있다
        for (JsonNode order : msg.get("data")) {
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(
                    order.get("symbol").asString());

            events.add(new OrderUpdateEvent(
                    tradingPair,
                    Instant.ofEpochMilli(order.get("updatedTime").asLong()),
                    SpotApiSpec.ORDER_STATE.get(order.get("orderStatus").asString()),
                    order.get("orderLinkId").asString(),
                    order.get("orderId").asString(),
                    null
            ));
        }
        return events;
    }
}