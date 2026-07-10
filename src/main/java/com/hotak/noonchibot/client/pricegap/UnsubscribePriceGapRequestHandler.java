package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketRequestHandler;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.PRICE_GAP_SUBSCRIPTION;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.UNSUBSCRIBE_METHOD;

@Component
@RequiredArgsConstructor
public class UnsubscribePriceGapRequestHandler implements WebSocketRequestHandler {
    private final PriceGapSubscriptionRegistry subscriptionRegistry;

    @Override
    public String method() {
        return UNSUBSCRIBE_METHOD;
    }

    @Override
    public String subscriptionType() {
        return PRICE_GAP_SUBSCRIPTION;
    }

    @Override
    public void handle(String sessionId, JsonNode subscription) {
        subscriptionRegistry.unsubscribe(sessionId, PriceGapSubscriptionParser.parse(subscription));
    }
}
