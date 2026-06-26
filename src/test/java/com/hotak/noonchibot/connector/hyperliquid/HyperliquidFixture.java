package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
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

final class HyperliquidFixture {
    private HyperliquidFixture() {}

    static final TradingPairSymbolRegistry BTC_ETH_REGISTRY = new SimpleTradingPairSymbolRegistry(
            Map.of("BTC-USDC", "BTC", "ETH-USDC", "ETH")
    );
    static final String USER = "0x1234567890abcdef1234567890abcdef12345678";
    static final String TRADING_PAIR = "BTC-USDC";
    static final String COIN = "BTC";
    static final String CLIENT_ORDER_ID = "0x11111111111111111111111111111111";
    static final String EXCHANGE_ORDER_ID = "77738308";
    static final long BOOK_TIME = 1_780_000_000_000L;
    static final Instant BOOK_INSTANT = Instant.ofEpochMilli(BOOK_TIME);
    static final List<OrderBookEntry> BIDS = List.of(new OrderBookEntry(3, new BigDecimal("50000.0"), new BigDecimal("0.10")));
    static final List<OrderBookEntry> ASKS = List.of(new OrderBookEntry(2, new BigDecimal("50100.0"), new BigDecimal("0.20")));

    static RestFixture l2BookSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of("type", "l2Book", "coin", COIN))
                        .build(),
                ok(l2BookBody())
        );
    }

    static WsResponse wsL2Book() {
        return new WsResponse("""
                {
                  "channel": "l2Book",
                  "data": %s
                }
                """.formatted(l2BookBody()), WsResponse.MessageType.TEXT);
    }

    static WsResponse wsTrades() {
        return new WsResponse("""
                {
                  "channel": "trades",
                  "data": [
                    {
                      "coin": "BTC",
                      "side": "B",
                      "px": "50050.0",
                      "sz": "0.01",
                      "hash": "0xabc",
                      "time": 1780000000100,
                      "tid": 123,
                      "users": ["0x1", "0x2"]
                    }
                  ]
                }
                """, WsResponse.MessageType.TEXT);
    }

    static WsResponse ack() {
        return new WsResponse("""
                {"channel":"subscriptionResponse","data":{"method":"subscribe"}}
                """, WsResponse.MessageType.TEXT);
    }

    static WsResponse error() {
        return new WsResponse("""
                {"channel":"error","data":"bad subscription"}
                """, WsResponse.MessageType.TEXT);
    }

    static RestFixture metaSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of("type", "meta"))
                        .build(),
                ok("""
                {
                  "universe": [
                    {"name": "BTC", "szDecimals": 5, "maxLeverage": 50},
                    {"name": "ETH", "szDecimals": 4, "maxLeverage": 50}
                  ]
                }
                """)
        );
    }

    static RestFixture orderStatusSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of("type", "orderStatus", "user", USER, "oid", CLIENT_ORDER_ID))
                        .build(),
                ok("""
                {
                  "status": "order",
                  "order": {
                    "order": {
                      "coin": "BTC",
                      "oid": 77738308,
                      "cloid": "0x11111111111111111111111111111111"
                    },
                    "status": "filled",
                    "statusTimestamp": 1780000000200
                  }
                }
                """)
        );
    }

    static RestFixture userFillsSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of("type", "userFills", "user", USER, "aggregateByTime", false))
                        .build(),
                ok("""
                [
                  {
                    "coin": "BTC",
                    "px": "50000.0",
                    "sz": "0.01",
                    "side": "B",
                    "time": 1780000000300,
                    "hash": "0xabc",
                    "oid": 77738308,
                    "crossed": false,
                    "fee": "0.01",
                    "tid": 456,
                    "feeToken": "USDC"
                  }
                ]
                """)
        );
    }

    static RestFixture clearinghouseStateSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                        .body(Map.of("type", "clearinghouseState", "user", USER))
                        .build(),
                ok("""
                {
                  "marginSummary": {
                    "accountValue": "1000.0",
                    "totalNtlPos": "200.0",
                    "totalRawUsd": "1000.0",
                    "totalMarginUsed": "50.0"
                  },
                  "withdrawable": "950.0",
                  "assetPositions": []
                }
                """)
        );
    }

    static RestFixture subAccountTransferSuccess(String subAccountAddress, BigDecimal amount, boolean masterToSub) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(DerivativeApiSpec.EXCHANGE_PATH_URL)
                        .body(Map.of(
                                "action", Map.of(
                                        "type", "subAccountTransfer",
                                        "subAccountUser", subAccountAddress,
                                        "isDeposit", masterToSub,
                                        "usd", amount.multiply(new BigDecimal("1000000")).longValue()
                                )
                        ))
                        .authRequired(true)
                        .build(),
                ok("""
                {
                  "status": "ok",
                  "response": {
                    "type": "default"
                  }
                }
                """)
        );
    }

    static WsResponse orderUpdates() {
        return new WsResponse("""
                {
                  "channel": "orderUpdates",
                  "data": [{
                    "order": {
                      "coin": "BTC",
                      "side": "B",
                      "limitPx": "50000.0",
                      "sz": "0.01",
                      "oid": 77738308,
                      "timestamp": 1780000000100,
                      "origSz": "0.01",
                      "cloid": "0x11111111111111111111111111111111"
                    },
                    "status": "filled",
                    "statusTimestamp": 1780000000200
                  }]
                }
                """, WsResponse.MessageType.TEXT);
    }

    static WsResponse userFills() {
        return new WsResponse("""
                {
                  "channel": "userFills",
                  "data": {
                    "isSnapshot": false,
                    "user": "0x1234567890abcdef1234567890abcdef12345678",
                    "fills": [{
                      "coin": "BTC",
                      "px": "50000.0",
                      "sz": "0.01",
                      "time": 1780000000300,
                      "hash": "0xabc",
                      "oid": 77738308,
                      "crossed": true,
                      "fee": "0.01",
                      "tid": 456,
                      "feeToken": "USDC"
                    }]
                  }
                }
                """, WsResponse.MessageType.TEXT);
    }

    static WsResponse clearinghouseState() {
        return new WsResponse("""
                {
                  "channel": "clearinghouseState",
                  "data": {
                    "assetPositions": [{
                      "type": "oneWay",
                      "position": {
                        "coin": "BTC",
                        "szi": "-0.02",
                        "entryPx": "50000.0",
                        "unrealizedPnl": "12.3"
                      }
                    }]
                  }
                }
                """, WsResponse.MessageType.TEXT);
    }

    private static String l2BookBody() {
        return """
                {
                  "coin": "BTC",
                  "time": 1780000000000,
                  "levels": [
                    [{"px": "50000.0", "sz": "0.10", "n": 3}],
                    [{"px": "50100.0", "sz": "0.20", "n": 2}]
                  ]
                }
                """;
    }
}
