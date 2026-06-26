package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceFixture;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.RestFixture;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hotak.noonchibot.testutils.FixtureUtils.ok;

final class BinanceSpotFixture {
    private BinanceSpotFixture() {}

    static final TradingPairSymbolRegistry BTC_ETH_SOL_REGISTRY = BinanceFixture.BTC_ETH_SOL_REGISTRY;

    static final String TRADING_PAIR = "BTC-USDT";
    static final String EXCHANGE_SYMBOL = "BTCUSDT";
    static final String CLIENT_ORDER_ID = "cid-1";
    static final String EXCHANGE_ORDER_ID = "12345";
    static final long SERVER_TIME = 1_780_000_000_000L;
    static final Instant SERVER_INSTANT = Instant.ofEpochMilli(SERVER_TIME);

    static final long SNAPSHOT_UPDATE_ID = 1024L;
    static final List<OrderBookEntry> SNAPSHOT_BIDS = List.of(new OrderBookEntry(0, new BigDecimal("50000.00"), new BigDecimal("0.10")));
    static final List<OrderBookEntry> SNAPSHOT_ASKS = List.of(new OrderBookEntry(0, new BigDecimal("50100.00"), new BigDecimal("0.20")));

    static final long DIFF_UPDATE_ID = 1030L;
    static final Instant DIFF_EVENT_TIME = Instant.ofEpochMilli(1_780_000_000_100L);
    static final List<OrderBookEntry> DIFF_BIDS = List.of(new OrderBookEntry(0, new BigDecimal("50010.00"), new BigDecimal("0.30")));
    static final List<OrderBookEntry> DIFF_ASKS = List.of(new OrderBookEntry(0, new BigDecimal("50110.00"), new BigDecimal("0.40")));

    static final long TRADE_ID = 777L;
    static final BigDecimal TRADE_PRICE = new BigDecimal("50050.00");
    static final BigDecimal TRADE_QTY = new BigDecimal("0.01");
    static final Instant TRADE_TIME = Instant.ofEpochMilli(1_780_000_000_200L);

    static final Instant ACCOUNT_TIME = Instant.ofEpochMilli(1_780_000_001_000L);
    static final Instant USER_STREAM_EVENT_TIME = Instant.ofEpochMilli(1_780_000_002_000L);

    static RestFixture depthSnapshotSuccess(String exchangeSymbol) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.SNAPSHOT_PATH_URL)
                        .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                        .build(),
                ok("""
                {
                  "lastUpdateId": 1024,
                  "bids": [["50000.00", "0.10"]],
                  "asks": [["50100.00", "0.20"]]
                }
                """)
        );
    }

    static WsResponse wsDepthUpdate(String exchangeSymbol) {
        return new WsResponse("""
                {
                  "e": "depthUpdate",
                  "E": 1780000000100,
                  "s": "%s",
                  "U": 1025,
                  "u": 1030,
                  "b": [["50010.00", "0.30"]],
                  "a": [["50110.00", "0.40"]]
                }
                """.formatted(exchangeSymbol), WsResponse.MessageType.TEXT);
    }

    static WsResponse wsTrade(String exchangeSymbol) {
        return new WsResponse("""
                {
                  "e": "trade",
                  "E": 1780000000200,
                  "s": "%s",
                  "t": 777,
                  "p": "50050.00",
                  "q": "0.01",
                  "T": 1780000000200,
                  "m": false
                }
                """.formatted(exchangeSymbol), WsResponse.MessageType.TEXT);
    }

    static WsResponse ackResponse(int id) {
        return new WsResponse("""
                {
                  "result": null,
                  "id": %d
                }
                """.formatted(id), WsResponse.MessageType.TEXT);
    }

    static WsResponse wsErrorResponse(int code, String message) {
        return new WsResponse("""
                {
                  "error": {
                    "code": %d,
                    "msg": "%s"
                  },
                  "id": 1
                }
                """.formatted(code, message), WsResponse.MessageType.TEXT);
    }

    static RestFixture accountSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.ACCOUNTS_PATH_URL)
                        .authRequired(true)
                        .build(),
                ok("""
                {
                  "updateTime": 1780000001000,
                  "balances": [
                    {"asset": "BTC", "free": "0.10", "locked": "0.02"},
                    {"asset": "USDT", "free": "1000.00", "locked": "50.00"}
                  ]
                }
                """)
        );
    }

    static RestFixture userTradesSuccess(String exchangeSymbol, String exchangeOrderId) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.MY_TRADES_PATH_URL)
                        .params(Map.of("symbol", exchangeSymbol, "orderId", exchangeOrderId))
                        .authRequired(true)
                        .build(),
                ok("""
                [
                  {
                    "id": 1001,
                    "orderId": 12345,
                    "price": "50000.00",
                    "qty": "0.01",
                    "quoteQty": "500.00",
                    "commission": "0.00001",
                    "commissionAsset": "BTC",
                    "time": 1780000002000,
                    "isMaker": true
                  },
                  {
                    "id": 1002,
                    "orderId": 12345,
                    "price": "50100.00",
                    "qty": "0.02",
                    "quoteQty": "1002.00",
                    "commission": "1.002",
                    "commissionAsset": "USDT",
                    "time": 1780000003000,
                    "isMaker": false
                  }
                ]
                """)
        );
    }

    static RestFixture orderStatusSuccess(String exchangeSymbol, String clientOrderId) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.ORDER_PATH_URL)
                        .params(Map.of("symbol", exchangeSymbol, "origClientOrderId", clientOrderId))
                        .authRequired(true)
                        .build(),
                ok("""
                {
                  "symbol": "BTCUSDT",
                  "orderId": 12345,
                  "clientOrderId": "cid-1",
                  "status": "FILLED",
                  "updateTime": 1780000002000
                }
                """)
        );
    }

    static WsResponse userStreamSubscriptionAck() {
        return new WsResponse("""
                {
                  "id": "req-1",
                  "status": 200,
                  "result": {
                    "subscriptionId": 0
                  }
                }
                """, WsResponse.MessageType.TEXT);
    }

    static WsResponse executionReportFilled() {
        return new WsResponse("""
                {
                  "subscriptionId": 0,
                  "event": {
                    "e": "executionReport",
                    "E": 1780000002000,
                    "s": "BTCUSDT",
                    "c": "cid-1",
                    "i": 12345,
                    "x": "TRADE",
                    "X": "FILLED",
                    "t": 1001,
                    "L": "50000.00",
                    "l": "0.01",
                    "n": "0.00001",
                    "N": "BTC",
                    "T": 1780000002000,
                    "m": true
                  }
                }
                """, WsResponse.MessageType.TEXT);
    }

    static WsResponse outboundAccountPosition() {
        return new WsResponse("""
                {
                  "subscriptionId": 0,
                  "event": {
                    "e": "outboundAccountPosition",
                    "E": 1780000002000,
                    "u": 1780000001999,
                    "B": [
                      {"a": "BTC", "f": "0.10", "l": "0.02"},
                      {"a": "USDT", "f": "1000.00", "l": "50.00"}
                    ]
                  }
                }
                """, WsResponse.MessageType.TEXT);
    }
}
