package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.orderbook.OrderBookRow;

import java.util.*;
import java.util.stream.Collectors;

public class OrderBookMessage implements Comparable<OrderBookMessage> {
    public final OrderBookMessageType type;
    private final Map<String, Object> content;
    private final double timestamp;

    public OrderBookMessage(OrderBookMessageType type, Map<String, Object> content, Double timestamp) {
        this.type = type;
        this.content = content;
        this.timestamp = (timestamp != null) ? timestamp : 0.0;
    }

    public long getUpdateId() {
        if (type == OrderBookMessageType.DIFF || type == OrderBookMessageType.SNAPSHOT) {
            return ((Number) content.get("update_id")).longValue();
        }
        return -1L;
    }

    public long getFirstUpdateId() {
        if (type == OrderBookMessageType.DIFF) {
            Object firstId = content.get("first_update_id");
            return (firstId != null) ? ((Number) firstId).longValue() : getUpdateId();
        }
        return -1L;
    }

    public long getTradeId() {
        if (type == OrderBookMessageType.TRADE) {
            return ((Number) content.get("trade_id")).longValue();
        }
        return -1L;
    }

    public String getTradingPair() {
        return (String) content.get("trading_pair");
    }

    @SuppressWarnings("unchecked")
    public List<OrderBookRow> getAsks() {
        List<List<Object>> asksRaw = (List<List<Object>>) content.get("asks");
        return asksRaw.stream()
                .map(row -> new OrderBookRow(
                        Double.parseDouble(row.get(0).toString()),
                        Double.parseDouble(row.get(1).toString()),
                        getUpdateId()))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public List<OrderBookRow> getBids() {
        List<List<Object>> bidsRaw = (List<List<Object>>) content.get("bids");
        return bidsRaw.stream()
                .map(row -> new OrderBookRow(
                        Double.parseDouble(row.get(0).toString()),
                        Double.parseDouble(row.get(1).toString()),
                        getUpdateId()))
                .collect(Collectors.toList());
    }

    public boolean hasUpdateId() {
        return type == OrderBookMessageType.DIFF || type == OrderBookMessageType.SNAPSHOT;
    }

    public boolean hasTradeId() {
        return type == OrderBookMessageType.TRADE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderBookMessage other = (OrderBookMessage) o;

        boolean typeEq = (this.type == other.type);
        boolean idEq = (this.hasUpdateId() && other.hasUpdateId() && this.getUpdateId() == other.getUpdateId())
                || (this.getTradeId() == other.getTradeId());

        return typeEq && idEq;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, getUpdateId(), getTradeId());
    }

    // --- Comparison (__lt__ 대응) ---

    @Override
    public int compareTo(OrderBookMessage other) {
        // 1. Update ID 비교
        if (this.hasUpdateId() && other.hasUpdateId()) {
            if (this.getUpdateId() != other.getUpdateId()) {
                return Long.compare(this.getUpdateId(), other.getUpdateId());
            }
        }

        // 2. Trade ID 비교
        if (this.hasTradeId() && other.hasTradeId()) {
            if (this.getTradeId() != other.getTradeId()) {
                return Long.compare(this.getTradeId(), other.getTradeId());
            }
        }

        // 3. Timestamp 비교
        if (Double.compare(this.timestamp, other.timestamp) != 0) {
            return Double.compare(this.timestamp, other.timestamp);
        }

        // 4. 타임스탬프까지 같으면 UpdateID가 있는 메시지를 우선함 (Python 로직 반영)
        if (this.hasUpdateId() && !other.hasUpdateId()) return -1;
        if (!this.hasUpdateId() && other.hasUpdateId()) return 1;

        return 0;
    }

    // Getters for base fields
    public OrderBookMessageType getType() { return type; }
    public Map<String, Object> getContent() { return content; }
    public double getTimestamp() { return timestamp; }
}
