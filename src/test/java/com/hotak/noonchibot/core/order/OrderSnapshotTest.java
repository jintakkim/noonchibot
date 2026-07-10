package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSnapshotTest {
    @Test
    void snapshotRoundTrip_restoresStableOrderFieldsOnly() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-01T00:00:05Z");
        OrderView view = new OrderView(
                "cid-1",
                "eid-1",
                "BTC-USDT",
                OrderType.LIMIT,
                TradeType.BUY,
                TimeInForce.GTC,
                false,
                OrderState.PARTIALLY_FILLED,
                new BigDecimal("1.0"),
                new BigDecimal("50000"),
                new BigDecimal("0.4"),
                new BigDecimal("20000"),
                new BigDecimal("0.6"),
                Set.of("trade-1"),
                Map.of("USDT", new BigDecimal("0.05")),
                createdAt,
                updatedAt
        );

        InFlightOrder restored = OrderSnapshot.from(Exchange.BINANCE_DERIVATIVE, view)
                .toInFlightOrder();

        OrderView restoredView = restored.toView();
        assertThat(restoredView.clientOrderId()).isEqualTo("cid-1");
        assertThat(restoredView.exchangeOrderId()).isEqualTo("eid-1");
        assertThat(restoredView.tradingPair()).isEqualTo("BTC-USDT");
        assertThat(restoredView.orderType()).isEqualTo(OrderType.LIMIT);
        assertThat(restoredView.tradeType()).isEqualTo(TradeType.BUY);
        assertThat(restoredView.timeInForce()).isEqualTo(TimeInForce.GTC);
        assertThat(restoredView.postOnly()).isFalse();
        assertThat(restoredView.state()).isEqualTo(OrderState.PARTIALLY_FILLED);
        assertThat(restoredView.amount()).isEqualByComparingTo("1.0");
        assertThat(restoredView.price()).isEqualByComparingTo("50000");
        assertThat(restoredView.executedBaseAmount()).isZero();
        assertThat(restoredView.executedQuoteAmount()).isZero();
        assertThat(restoredView.remainingBaseAmount()).isEqualByComparingTo("1.0");
        assertThat(restoredView.processedTradeIds()).isEmpty();
        assertThat(restoredView.accumulatedFees()).isEmpty();
        assertThat(restoredView.createdAt()).isEqualTo(createdAt);
        assertThat(restoredView.updatedAt()).isEqualTo(updatedAt);
    }
}
