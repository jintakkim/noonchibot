package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEvent;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BinanceBalanceUpdateParser implements UserStreamEventParser {
    @Override
    public boolean canParse(JsonNode msg) {
        return "outboundAccountPosition".equals(msg.path("e").asString());
    }

    @Override
    public List<ExchangeEvent> parse(JsonNode msg) {
        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        List<ExchangeEvent> events = new ArrayList<>();
        for (JsonNode balance : msg.get("B")) {
            events.add(new BalanceUpdateEvent(
                    balance.get("a").asString(),
                    balance.get("f").asDecimal(),
                    balance.get("l").asDecimal(),
                    eventTime
            ));
        }
        return events;
    }
}
