package com.hotak.noonchibot.connector.binance;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BinanceResponseCodeParser {
    public static int parseCode(JsonNode res) {
        if(res == null || !res.has("code")) {
            throw new IllegalArgumentException("Invalid response: " + res);
        }
        return res.get("code").asInt();
    }
}
