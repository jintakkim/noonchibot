package com.hotak.noonchibot.core.pricegap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceGapSubscriptionRegistryTest {
    @Test
    void subscriptionsAreSharedByKeyAndRemovedBySubscriber() {
        PriceGapSubscriptionRegistry registry = new PriceGapSubscriptionRegistry();
        PriceGapSubscriptionKey key = new PriceGapSubscriptionKey("btc-usdt");
        PriceGapSubscriptionKey secondKey = new PriceGapSubscriptionKey("eth-usdt");

        registry.subscribe("session-1", java.util.Set.of(key, secondKey));
        registry.subscribe("session-2", key);

        assertThat(registry.activeKeys()).containsExactlyInAnyOrder(key, secondKey);
        assertThat(registry.subscribers(key)).containsExactlyInAnyOrder("session-1", "session-2");
        assertThat(registry.subscriptionsBySubscriber().get("session-1"))
                .containsExactlyInAnyOrder(key, secondKey);

        registry.removeSubscriber("session-1");
        assertThat(registry.subscribers(key)).containsExactly("session-2");

        registry.removeSubscriber("session-2");
        assertThat(registry.activeKeys()).isEmpty();
    }
}
