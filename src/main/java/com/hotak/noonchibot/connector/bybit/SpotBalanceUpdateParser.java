package com.hotak.noonchibot.connector.bybit;

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
        return "wallet".equals(msg.get("topic").asString());
    }

    @Override
    public List<Event> parse(JsonNode msg) {
        Instant eventTime = Instant.ofEpochMilli(msg.get("creationTime").asLong());
        List<Event> events = new ArrayList<>();
        JsonNode coinList = msg.get("data").get(0).get("coin");
        for (JsonNode balance : coinList) {
            BigDecimal walletBalance = balance.get("walletBalance").asDecimal();
            BigDecimal locked = balance.get("locked").asDecimal();
            events.add(new BalanceUpdateEvent(
                    balance.get("coin").asString(),
                    walletBalance,
                    walletBalance.subtract(locked),
                    eventTime
            ));
        }
        return events;
    }
}