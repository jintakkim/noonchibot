package com.hotak.noonchibot.connector.upbit;

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
public class SpotExecutionReportParser implements UserStreamEventParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public boolean canParse(JsonNode msg) {
        return "myOrder".equals(msg.path("type").asString());
    }

    @Override
    public List<ExchangeEvent> parse(JsonNode event) {
        List<ExchangeEvent> events = new ArrayList<>();

        String state = event.path("state").asString();
        String exchangeOrderId = event.path("uuid").asString();
        String clientOrderId = event.path("identifier").asString();
        String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(event.path("code").asString());

        if ("trade".equals(state)) {
            // price/volume = 이번 체결의 값 (단일 체결)
            // avg_price/executed_volume/executed_funds = 주문 전체 누적 값
            BigDecimal fillPrice = event.path("price").asDecimal();
            BigDecimal fillQty = event.path("volume").asDecimal();
            BigDecimal fillAmount = fillPrice.multiply(fillQty);
            String feeCurrency = event.path("code").asString().split("-")[0];

            events.add(new TradeUpdateEvent(
                    event.path("trade_uuid").asString(),
                    clientOrderId,
                    exchangeOrderId,
                    tradingPair,
                    Instant.ofEpochMilli(event.path("trade_timestamp").asLong()),
                    fillPrice,
                    fillQty,
                    fillAmount,
                    new TokenAmount(
                            feeCurrency,
                            event.path("trade_fee").asDecimal()
                    ),
                    event.path("is_maker").asBoolean()
            ));
        }

        events.add(new OrderUpdateEvent(
                tradingPair,
                Instant.ofEpochMilli(event.path("timestamp").asLong()),
                SpotApiSpec.parseOrderState(event),
                clientOrderId,
                exchangeOrderId,
                null
        ));

        return events;
    }
}