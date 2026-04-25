package com.hotak.noonchibot.connector.upbit;

import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEvent;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SpotBalanceUpdateParser implements UserStreamEventParser {
    @Override
    public boolean canParse(JsonNode msg) {
        return "myAsset".equals(msg.path("type").asString());
    }

    @Override
    public List<ExchangeEvent> parse(JsonNode msg) {
        Instant eventTime = Instant.ofEpochMilli(msg.path("asset_timestamp").asLong());
        List<ExchangeEvent> events = new ArrayList<>();
        for (JsonNode assets : msg.path("asset")) {
            BigDecimal balance = assets.path("balance").asDecimal();
            BigDecimal locked = assets.path("locked").asDecimal();
            events.add(new BalanceUpdateEvent(
                    assets.path("currency").asString(),
                    balance.add(locked),
                    balance,
                    eventTime
            ));
        }
        return events;
    }
}