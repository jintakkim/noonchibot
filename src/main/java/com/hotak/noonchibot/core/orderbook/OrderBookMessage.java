package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.TradeType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class OrderBookMessage {
    public enum Type {
        //특정 시점 스냅샷
        SNAPSHOT,
        //차이
        DIFF,
        //단일 거래
        TRADE
    }
    private final Type type;
    private final Instant timestamp;
    private final String tradingPair;

    @Getter
    @ToString
    public static class DiffMessage extends OrderBookMessage {
        private final long updateId;
        private final List<OrderBookEntry> bids;
        private final List<OrderBookEntry> asks;

        public DiffMessage(Instant timestamp, String tradingPair, long updateId, List<OrderBookEntry> bids, List<OrderBookEntry> asks) {
            super(Type.DIFF, timestamp, tradingPair);
            this.updateId = updateId;
            this.bids = bids;
            this.asks = asks;
        }
    }

    @Getter
    public static class SnapshotMessage extends OrderBookMessage {
        private final long updateId;
        private final List<OrderBookEntry> bids;
        private final List<OrderBookEntry> asks;

        public SnapshotMessage(Instant timestamp, String tradingPair, long updateId, List<OrderBookEntry> bids, List<OrderBookEntry> asks) {
            super(Type.SNAPSHOT, timestamp, tradingPair);
            this.updateId = updateId;
            this.bids = bids;
            this.asks = asks;
        }
    }

    @Getter
    @ToString
    public static class TradeMessage extends OrderBookMessage {
        private final long tradeId;
        private final BigDecimal price;
        private final BigDecimal amount;
        private final TradeType tradeType;

        public TradeMessage(Instant timestamp, String tradingPair, long tradeId, BigDecimal price, BigDecimal amount, TradeType tradeType) {
            super(Type.TRADE, timestamp, tradingPair);
            this.tradeId = tradeId;
            this.price = price;
            this.amount = amount;
            this.tradeType = tradeType;
        }
    }
}