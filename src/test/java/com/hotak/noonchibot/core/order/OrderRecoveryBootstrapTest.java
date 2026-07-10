package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;
import com.hotak.noonchibot.core.trade.TokenAmount;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderRecoveryBootstrapTest {
    @Test
    void onStart_restoresOrderAndReconcilesStatusAndTrades() {
        OrderSnapshotRepository snapshotRepository = mock(OrderSnapshotRepository.class);
        OrderStatusReader orderStatusReader = mock(OrderStatusReader.class);
        OrderTradeReader orderTradeReader = mock(OrderTradeReader.class);
        OrderTracker tracker = new OrderTracker(
                new TestEventPublisher(),
                mock(TradeRepository.class),
                snapshotRepository,
                new TestEventSubscriber()
        );
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(5);
        OrderView persisted = new OrderView(
                "cid-1",
                null,
                "BTC-USDT",
                OrderType.LIMIT,
                TradeType.BUY,
                TimeInForce.GTC,
                false,
                OrderState.PENDING_CREATE,
                BigDecimal.ONE,
                new BigDecimal("50000"),
                new BigDecimal("0.7"),
                new BigDecimal("35000"),
                new BigDecimal("0.3"),
                Set.of("stale-trade"),
                Map.of("USDT", new BigDecimal("99")),
                createdAt,
                createdAt
        );
        OrderSnapshot snapshot = OrderSnapshot.from(Exchange.BINANCE_DERIVATIVE, persisted);
        when(snapshotRepository.findByExchangeAndStateNotIn(any(), any())).thenReturn(List.of(snapshot));
        when(orderStatusReader.fetch("BTC-USDT", "cid-1")).thenReturn(new OrderEvent.StatusReceived(
                "BTC-USDT",
                "cid-1",
                "eid-1",
                OrderState.OPEN,
                updatedAt
        ));
        when(orderTradeReader.fetch("cid-1", "eid-1", "BTC-USDT")).thenReturn(new TradeEvent.Received(
                "cid-1",
                "eid-1",
                "BTC-USDT",
                List.of(new TradeEvent.Fill(
                        "trade-1",
                        updatedAt.minusSeconds(1),
                        new BigDecimal("50000"),
                        new BigDecimal("0.4"),
                        new BigDecimal("20000"),
                        new TokenAmount("USDT", new BigDecimal("8")),
                        false
                ))
        ));

        new OrderRecoveryBootstrap(
                Exchange.BINANCE_DERIVATIVE,
                snapshotRepository,
                orderStatusReader,
                orderTradeReader,
                tracker
        ).onStart();

        OrderView restored = tracker.getOrderByClientId("cid-1").orElseThrow();
        assertThat(restored.exchangeOrderId()).isEqualTo("eid-1");
        assertThat(restored.state()).isEqualTo(OrderState.OPEN);
        assertThat(restored.executedBaseAmount()).isEqualByComparingTo("0.4");
        assertThat(restored.executedQuoteAmount()).isEqualByComparingTo("20000");
        assertThat(restored.processedTradeIds()).containsExactly("trade-1");
        assertThat(restored.accumulatedFees()).containsEntry("USDT", new BigDecimal("8"));
        assertThat(restored.updatedAt()).isEqualTo(updatedAt);
    }
}
