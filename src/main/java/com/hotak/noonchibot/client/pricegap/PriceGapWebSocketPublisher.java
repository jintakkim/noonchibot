package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshot;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotPublisher;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PriceGapWebSocketPublisher implements PriceGapSnapshotPublisher {
    private final PriceGapSubscriptionRegistry subscriptionRegistry;
    private final WebSocketSessions sessions;

    @Override
    public void publish(PriceGapSnapshot snapshot) {
        PriceGapWebSocketMessage message = new PriceGapWebSocketMessage(snapshot);
        subscriptionRegistry.subscribers(snapshot.key()).forEach(sessionId ->
                sessions.send(sessionId, message)
        );
    }
}
