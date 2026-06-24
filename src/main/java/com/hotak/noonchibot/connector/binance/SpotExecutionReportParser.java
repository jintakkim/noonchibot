package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.order.OrderUpdateDto;
import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.Event;
import com.hotak.noonchibot.core.trade.TokenAmount;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
class SpotExecutionReportParser implements UserStreamEventParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public boolean canParse(JsonNode msg) {
        return "executionReport".equals(msg.path("e").asString());
    }

    @Override
    public List<Event> parse(JsonNode event) {
        List<Event> events = new ArrayList<>();
        String executionType = event.get("x").asString();
        String clientOrderId = "CANCELED".equals(executionType) ? event.get("C").asString() : event.get("c").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(event.get("s").asString());

        if ("TRADE".equals(executionType)) {
            BigDecimal fillQty = event.get("l").asDecimal();
            BigDecimal fillPrice = event.get("L").asDecimal();
            events.add(new TradeUpdateEvent(
                    event.get("t").asString(),
                    clientOrderId,
                    event.get("i").asString(),
                    tradingPair,
                    Instant.ofEpochMilli(event.get("T").asLong()),
                    fillPrice,
                    fillQty,
                    fillQty.multiply(fillPrice),
                    new TokenAmount(
                            event.get("N").asString(),
                            event.get("n").asDecimal()
                    ),
                    event.get("m").asBoolean()
            ));
        }

        events.add(new OrderUpdateDto(
                tradingPair,
                Instant.ofEpochMilli(event.get("E").asLong()),
                SpotApiSpec.ORDER_STATE.get(event.get("X").asString()),
                clientOrderId,
                event.get("i").asString(),
                null
        ));

        return events;
    }
}
