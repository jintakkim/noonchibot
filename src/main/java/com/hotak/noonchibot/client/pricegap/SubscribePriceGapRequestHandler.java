package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketRequestHandler;
import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotStore;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshot;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.PRICE_GAP_SUBSCRIPTION;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.SUBSCRIBE_METHOD;

import java.util.List;
import java.util.Set;
import java.util.Comparator;

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
        Set<PriceGapSubscriptionKey> keys = PriceGapSubscriptionParser.parse(subscription);
        subscriptionRegistry.subscribe(sessionId, keys);
        List<PriceGapSnapshot> snapshots = keys.stream()
                .flatMap(key -> snapshotStore.latest(key).stream())
                .sorted(Comparator.comparing(snapshot -> snapshot.key().tradingPair()))
                .toList();
        if (snapshots.isEmpty()) {
            log.warn("Price gap snapshots are not available yet; subscription registered: sessionId={}, pairs={}",
                    sessionId, keys.stream().map(PriceGapSubscriptionKey::tradingPair).toList());
        } else {
            sessions.send(sessionId, new PriceGapWebSocketMessage(snapshots));
        }
    }
}
