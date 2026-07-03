package com.hotak.noonchibot.core.pricegap;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.price.LastTradePrice;
import com.hotak.noonchibot.core.price.StableQuotePriceConverter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceGapSnapshotProducerTest {
    private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

    @Test
    void onTick_readsEachTrackerOnceAndPublishesSharedNormalizedSnapshot() {
        PriceGapSubscriptionKey key = new PriceGapSubscriptionKey("BTC-USDT");
        PriceGapSubscriptionRegistry registry = new PriceGapSubscriptionRegistry();
        registry.subscribe("session-1", key);
        registry.subscribe("session-2", key);
        PriceGapSnapshotStore store = new PriceGapSnapshotStore();
        List<PriceGapSnapshot> published = new ArrayList<>();

        OrderBookTracker binance = tracker("BTC-USDT", "60000");
        OrderBookTracker hyperliquid = tracker("BTC-USDC", "60000");
        OrderBookTracker stableRate = tracker("USDC-USDT", "0.9995");
        PriceGapSnapshotProducer producer = new PriceGapSnapshotProducer(
                registry,
                store,
                published::add,
                List.of(new PriceGapFeedDefinition(
                        key,
                        Map.of(
                                Exchange.BINANCE_DERIVATIVE, "BTC-USDT",
                                Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC"
                        ),
                        Exchange.BINANCE_SPOT,
                        "USDC-USDT"
                )),
                Map.of(
                        Exchange.BINANCE_DERIVATIVE, binance,
                        Exchange.HYPERLIQUID_DERIVATIVE, hyperliquid,
                        Exchange.BINANCE_SPOT, stableRate
                ),
                new StableQuotePriceConverter()
        );

        producer.onTick(NOW);

        assertThat(published).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.referencePrice()).isEqualByComparingTo("59985");
            assertThat(snapshot.exchanges()).hasSize(2);
            assertThat(snapshot.spread().lowestExchange()).isEqualTo(Exchange.HYPERLIQUID_DERIVATIVE);
            assertThat(snapshot.spread().highestExchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
        });
        assertThat(store.latest(key)).contains(published.getFirst());
        verify(binance).findLastTradePrice("BTC-USDT");
        verify(hyperliquid).findLastTradePrice("BTC-USDC");
        verify(stableRate).findLastTradePrice("USDC-USDT");
    }

    @Test
    void onTick_withoutSubscribersDoesNotReadTrackers() {
        PriceGapSubscriptionKey key = new PriceGapSubscriptionKey("BTC-USDT");
        OrderBookTracker tracker = mock(OrderBookTracker.class);
        PriceGapSnapshotProducer producer = new PriceGapSnapshotProducer(
                new PriceGapSubscriptionRegistry(),
                new PriceGapSnapshotStore(),
                PriceGapSnapshotPublisher.NOOP,
                List.of(new PriceGapFeedDefinition(
                        key,
                        Map.of(Exchange.BINANCE_DERIVATIVE, "BTC-USDT"),
                        null,
                        null
                )),
                Map.of(Exchange.BINANCE_DERIVATIVE, tracker),
                new StableQuotePriceConverter()
        );

        producer.onTick(NOW);

        verify(tracker, never()).findLastTradePrice("BTC-USDT");
    }

    private OrderBookTracker tracker(String tradingPair, String price) {
        OrderBookTracker tracker = mock(OrderBookTracker.class);
        when(tracker.findLastTradePrice(tradingPair)).thenReturn(Optional.of(new LastTradePrice(
                new BigDecimal(price),
                NOW
        )));
        return tracker;
    }
}
