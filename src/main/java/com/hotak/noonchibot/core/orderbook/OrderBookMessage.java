package com.hotak.noonchibot.core.orderbook;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    @Getter
    public static class DiffMessage extends OrderBookMessage {
        private final long updateId;
        private final List<OrderBookEntry> bids;
        private final List<OrderBookEntry> asks;

        public DiffMessage(Type type, Instant timestamp, long updateId, List<OrderBookEntry> bids, List<OrderBookEntry> asks) {
            super(type, timestamp);
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

        public SnapshotMessage(Type type, Instant timestamp, long updateId, List<OrderBookEntry> bids, List<OrderBookEntry> asks) {
            super(type, timestamp);
            this.updateId = updateId;
            this.bids = bids;
            this.asks = asks;
        }
    }
}