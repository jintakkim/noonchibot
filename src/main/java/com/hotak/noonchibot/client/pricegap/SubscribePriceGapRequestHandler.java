package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketRequestHandler;
import com.hotak.noonchibot.client.websocket.WebSocketRequestException;
import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotStore;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.INVALID_SUBSCRIPTION;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.PAIR_REQUIRED;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.PAIR_FIELD;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.PRICE_GAP_SUBSCRIPTION;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.SUBSCRIBE_METHOD;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscribePriceGapRequestHandler implements WebSocketRequestHandler {
    private final PriceGapSubscriptionRegistry subscriptionRegistry;
    private final PriceGapSnapshotStore snapshotStore;
    private final WebSocketSessions sessions;

    @Override
    public String method() {
        return SUBSCRIBE_METHOD;
    }

    @Override
    public String subscriptionType() {
        return PRICE_GAP_SUBSCRIPTION;
    }

    @Override
    public void handle(String sessionId, JsonNode subscription) {
        PriceGapSubscriptionKey key = new PriceGapSubscriptionKey(pair(subscription));
        subscriptionRegistry.subscribe(sessionId, key);
        snapshotStore.latest(key).ifPresentOrElse(
                snapshot -> sessions.send(sessionId, new PriceGapWebSocketMessage(snapshot)),
                () -> log.warn(
                        "Price gap snapshot is not available yet; subscription registered: sessionId={}, pair={}",
                        sessionId,
                        key.tradingPair()
                )
        );
    }

    private String pair(JsonNode subscription) {
        if (subscription == null || subscription.get(PAIR_FIELD) == null) {
            throw new WebSocketRequestException(INVALID_SUBSCRIPTION, PAIR_REQUIRED);
        }
        return subscription.get(PAIR_FIELD).asString();
    }
}
