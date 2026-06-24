package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.Event;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class SpotBalanceUpdateParser implements UserStreamEventParser {
    @Override
    public boolean canParse(JsonNode msg) {
        return "outboundAccountPosition".equals(msg.path("e").asString());
    }

    @Override
    public List<Event> parse(JsonNode msg) {
        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        List<Event> events = new ArrayList<>();
        for (JsonNode balance : msg.get("B")) {
            BigDecimal free = balance.get("f").asDecimal();
            BigDecimal locked = balance.get("l").asDecimal();
            events.add(new BalanceUpdateEvent(
                    balance.get("a").asString(),
                    free.add(locked),
                    free,
                    eventTime
            ));
        }
        return events;
    }
}
