package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketRequestException;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.INVALID_SUBSCRIPTION;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.PAIR_OR_PAIRS_REQUIRED;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.PAIRS_FIELD;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.PAIR_FIELD;

final class PriceGapSubscriptionParser {
    private PriceGapSubscriptionParser() {
    }

    static Set<PriceGapSubscriptionKey> parse(JsonNode subscription) {
        if (subscription == null) {
            throw invalidSubscription();
        }
        Set<PriceGapSubscriptionKey> keys = new HashSet<>();
        JsonNode pair = subscription.get(PAIR_FIELD);
        if (pair != null && !pair.isNull()) {
            keys.add(new PriceGapSubscriptionKey(pair.asString()));
        }
        JsonNode pairs = subscription.get(PAIRS_FIELD);
        if (pairs != null && pairs.isArray()) {
            pairs.forEach(value -> keys.add(new PriceGapSubscriptionKey(value.asString())));
        }
        if (keys.isEmpty()) {
            throw invalidSubscription();
        }
        return Set.copyOf(keys);
    }

    private static WebSocketRequestException invalidSubscription() {
        return new WebSocketRequestException(INVALID_SUBSCRIPTION, PAIR_OR_PAIRS_REQUIRED);
    }
}
