package com.hotak.noonchibot.core.event.internal.orderbook;

import com.hotak.noonchibot.core.event.internal.CoreEvent;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public sealed interface OrderBookEvent extends CoreEvent {
    record TrackingRequested(String tradingPair) implements OrderBookEvent {}
    record TrackingBatchRequested(Set<String> tradingPairs) implements OrderBookEvent {}

    record SnapshotReceived(
            String tradingPair,
            long updateId,
            List<OrderBookEntry>bids,
            List<OrderBookEntry> asks,
            Instant timestamp
    ) implements OrderBookEvent {}

    record DiffReceived(
            String tradingPair,
            long updateId,
            List<OrderBookEntry> bids,
            List<OrderBookEntry> asks,
            Instant timestamp
    ) implements OrderBookEvent {}

    record TradeReceived(
            String tradingPair,
            long tradeId,
            BigDecimal price,
            BigDecimal amount,
            TradeType tradeType,
            Instant timestamp
    ) implements OrderBookEvent {}
}
