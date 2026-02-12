package com.hotak.noonchibot.connector.exchange.binance;

import com.hotak.noonchibot.core.datatype.OrderBookMessageType;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.datatype.OrderBookMessage;

import java.util.HashMap;
import java.util.Map;

public class BinanceOrderBook extends OrderBook {

    /**
     * 바이낸스 스냅샷 API 응답을 OrderBookMessage로 변환
     */
    public static OrderBookMessage snapshotMessageFromExchange(
            Map<String, Object> msg,
            double timestamp,
            Map<String, Object> metadata) {

        if (metadata != null) {
            msg.putAll(metadata);
        }

        Map<String, Object> content = new HashMap<>();
        content.put("trading_pair", msg.get("trading_pair"));
        content.put("update_id", msg.get("lastUpdateId")); // 바이낸스 키: lastUpdateId
        content.put("bids", msg.get("bids"));
        content.put("asks", msg.get("asks"));

        return new OrderBookMessage(OrderBookMessageType.SNAPSHOT, content, timestamp);
    }

    /**
     * 바이낸스 웹소켓 Diff(Depth Update) 메시지를 OrderBookMessage로 변환
     */
    public static OrderBookMessage diffMessageFromExchange(
            Map<String, Object> msg,
            Double timestamp,
            Map<String, Object> metadata) {

        if (metadata != null) {
            msg.putAll(metadata);
        }

        Map<String, Object> content = new HashMap<>();
        content.put("trading_pair", msg.get("trading_pair"));
        content.put("first_update_id", msg.get("U")); // 바이낸스 키: U (First update ID)
        content.put("update_id", msg.get("u"));       // 바이낸스 키: u (Final update ID)
        content.put("bids", msg.get("b"));            // 바이낸스 키: b (bids)
        content.put("asks", msg.get("a"));            // 바이낸스 키: a (asks)

        return new OrderBookMessage(OrderBookMessageType.DIFF, content, timestamp);
    }

    /**
     * 바이낸스 웹소켓 Trade 메시지를 OrderBookMessage로 변환
     */
    public static OrderBookMessage tradeMessageFromExchange(
            Map<String, Object> msg,
            Map<String, Object> metadata) {

        if (metadata != null) {
            msg.putAll(metadata);
        }

        // 바이낸스 'E'는 밀리초 타임스탬프
        double ts = ((Number) msg.get("E")).doubleValue();
        // 바이낸스 'm': true 이면 Buyer가 Maker이므로 실체결은 SELL
        boolean isBuyerMaker = (boolean) msg.get("m");

        Map<String, Object> content = new HashMap<>();
        content.put("trading_pair", msg.get("trading_pair"));
        content.put("trade_type", isBuyerMaker ? (double) TradeType.SELL.getValue() : (double)TradeType.BUY.getValue());
        content.put("trade_id", msg.get("t"));        // 바이낸스 키: t (Trade ID)
        content.put("update_id", (long)ts);           // Trade는 보통 타임스탬프를 update_id로 사용
        content.put("price", msg.get("p"));           // 바이낸스 키: p (Price)
        content.put("amount", msg.get("q"));          // 바이낸스 키: q (Quantity)

        // 초 단위 타임스탬프로 변환하여 전달 (ms * 1e-3)
        return new OrderBookMessage(OrderBookMessageType.TRADE, content, ts * 0.001);
    }
}