package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketSessions;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshot;
import com.hotak.noonchibot.core.pricegap.PriceGapSnapshotPublisher;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionKey;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class PriceGapWebSocketPublisher implements PriceGapSnapshotPublisher {
    private final PriceGapSubscriptionRegistry subscriptionRegistry;
    private final WebSocketSessions sessions;

    @Override
    public void publish(List<PriceGapSnapshot> snapshots) {
        Map<PriceGapSubscriptionKey, PriceGapSnapshot> snapshotsByKey = snapshots.stream()
                .collect(Collectors.toMap(PriceGapSnapshot::key, Function.identity()));
        subscriptionRegistry.subscriptionsBySubscriber().forEach((sessionId, keys) -> {
            List<PriceGapSnapshot> subscribedSnapshots = keys.stream()
                    .map(snapshotsByKey::get)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(snapshot -> snapshot.key().tradingPair()))
                    .toList();
            if (!subscribedSnapshots.isEmpty()) {
                sessions.send(sessionId, new PriceGapWebSocketMessage(subscribedSnapshots));
            }
        });
    }
}
