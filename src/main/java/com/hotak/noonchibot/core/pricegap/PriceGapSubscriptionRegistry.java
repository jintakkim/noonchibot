package com.hotak.noonchibot.core.pricegap;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PriceGapSubscriptionRegistry {
    private final ConcurrentMap<PriceGapSubscriptionKey, Set<String>> subscribersByKey =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<PriceGapSubscriptionKey>> keysBySubscriber =
            new ConcurrentHashMap<>();

    public void subscribe(String subscriberId, PriceGapSubscriptionKey key) {
        subscribersByKey.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        keysBySubscriber.computeIfAbsent(subscriberId, ignored -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public void unsubscribe(String subscriberId, PriceGapSubscriptionKey key) {
        remove(subscribersByKey, key, subscriberId);
        remove(keysBySubscriber, subscriberId, key);
    }

    public void removeSubscriber(String subscriberId) {
        Set<PriceGapSubscriptionKey> keys = keysBySubscriber.remove(subscriberId);
        if (keys == null) {
            return;
        }
        keys.forEach(key -> remove(subscribersByKey, key, subscriberId));
    }

    public Set<PriceGapSubscriptionKey> activeKeys() {
        return Set.copyOf(subscribersByKey.keySet());
    }

    public Set<String> subscribers(PriceGapSubscriptionKey key) {
        return Set.copyOf(subscribersByKey.getOrDefault(key, Set.of()));
    }

    private <K, V> void remove(ConcurrentMap<K, Set<V>> map, K key, V value) {
        map.computeIfPresent(key, (ignored, values) -> {
            values.remove(value);
            return values.isEmpty() ? null : values;
        });
    }
}
