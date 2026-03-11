package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BinanceOrderBook extends OrderBook {
    public BinanceOrderBook() {
        super(true);
    }

    /**
     * 거래소 REST 응답으로부터 스냅샷 메시지 생성
     *
     * @param msg       거래소 스냅샷 응답 (lastUpdateId, bids, asks 포함)
     * @param tradingPair 거래쌍 (e.g. "BTC-USDT")
     */
    public static OrderBookMessage.SnapshotMessage snapshotMessageFromExchange(JsonNode msg, String tradingPair) {
        long updateId = msg.get("lastUpdateId").asLong();
        Instant eventTime = Instant.ofEpochSecond(msg.get("eventTime").asLong());
        List<OrderBookEntry> bids = parseEntries(msg.get("bids"));
        List<OrderBookEntry> asks = parseEntries(msg.get("asks"));

        return new OrderBookMessage.SnapshotMessage(eventTime, tradingPair, updateId, bids, asks);
    }

    /**
     * WebSocket diff 이벤트로부터 diff 메시지 생성
     * Binance diff 포맷: { "U": firstUpdateId, "u": lastUpdateId, "b": bids, "a": asks }
     */
    public static OrderBookMessage.DiffMessage diffMessageFromExchange(JsonNode msg, String tradingPair) {

        long firstUpdateId = msg.get("U").asLong();
        long updateId = msg.get("u").asLong();
        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        List<OrderBookEntry> bids = parseEntries(msg.get("b"));
        List<OrderBookEntry> asks = parseEntries(msg.get("a"));

        return new OrderBookMessage.DiffMessage(eventTime, tradingPair, updateId, bids, asks);
    }

    /**
     * WebSocket trade 이벤트로부터 trade 메시지 생성
     * Binance trade 포맷: { "E": eventTime, "t": tradeId, "p": price, "q": qty, "m": isBuyerMaker }
     */
    public static OrderBookMessage.TradeMessage tradeMessageFromExchange(JsonNode msg, String tradingPair) {

        Instant eventTime = Instant.ofEpochMilli(msg.get("E").asLong());
        // m=true → maker가 buyer → taker는 seller (SELL), m=false → BUY
        TradeType tradeType = msg.get("m").asBoolean() ? TradeType.SELL : TradeType.BUY;
        long tradeId = msg.get("t").asLong();
        BigDecimal price = msg.get("p").asDecimal();
        BigDecimal amount = msg.get("q").asDecimal();
        return new OrderBookMessage.TradeMessage(eventTime, tradingPair, tradeId, price, amount,tradeType);
    }

    private static List<OrderBookEntry> parseEntries(JsonNode arrayNode) {
        List<OrderBookEntry> entries = new ArrayList<>(arrayNode.size());
        for (JsonNode entry : arrayNode) {
            BigDecimal price = entry.get(0).asDecimal();
            BigDecimal amount = entry.get(1).asDecimal();
            entries.add(new OrderBookEntry(0L, price, amount));
        }
        return entries;
    }
}
