package com.hotak.noonchibot.core.pricegap;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PriceGapSnapshotStore {
    private final ConcurrentMap<PriceGapSubscriptionKey, PriceGapSnapshot> latestSnapshots =
            new ConcurrentHashMap<>();

    public void update(PriceGapSnapshot snapshot) {
        latestSnapshots.put(snapshot.key(), snapshot);
    }

    public Optional<PriceGapSnapshot> latest(PriceGapSubscriptionKey key) {
        return Optional.ofNullable(latestSnapshots.get(key));
    }
}
