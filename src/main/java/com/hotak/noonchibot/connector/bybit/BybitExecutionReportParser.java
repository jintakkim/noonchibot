package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.datatype.TradeUpdateEvent;
import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.ExchangeEvent;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
import com.hotak.noonchibot.core.trade.fee.TokenAmount;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class BybitExecutionReportParser implements UserStreamEventParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public boolean canParse(JsonNode msg) {
        return "execution".equals(msg.path("topic").asString());
    }

    @Override
    public List<ExchangeEvent> parse(JsonNode event) {
        List<ExchangeEvent> events = new ArrayList<>();
        String executionType = event.get("execType").asString();
        String clientOrderId = event.get("orderLinkId").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(event.get("symbol").asString());

        if ("Trade".equals(executionType)) {
            BigDecimal fillQty = event.get("execQty").asDecimal();
            BigDecimal fillPrice = event.get("execPrice").asDecimal();
            events.add(new TradeUpdateEvent(
                    event.get("execId").asString(),
                    clientOrderId,
                    event.get("orderId").asString(),
                    tradingPair,
                    Instant.ofEpochMilli(event.get("execTime").asLong()),
                    fillPrice,
                    fillQty,
                    event.get("execValue").asDecimal(),
                    new TokenAmount(
                            event.get("feeCurrency").asString(),
                            event.get("execFee").asDecimal()
                    ),
                    event.get("isMaker").asBoolean()
            ));
        }

        events.add(new OrderUpdateEvent(
                tradingPair,
                Instant.ofEpochMilli(event.get("updatedTime").asLong()),
                BybitApiSpec.ORDER_STATE.get(event.get("orderStatus").asString()),
                clientOrderId,
                event.get("orderId").asString(),
                null
        ));

        return events;
    }
}
