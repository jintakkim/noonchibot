package com.hotak.noonchibot.connector.binance.derivative;

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
import java.util.stream.Collectors;

import static com.hotak.noonchibot.testutils.FixtureUtils.badRequest;
import static com.hotak.noonchibot.testutils.FixtureUtils.ok;

public final class BinanceDerivativeFixture {
    private BinanceDerivativeFixture() {}

    public static final TradingPairSymbolRegistry BTC_ETH_SOL_REGISTRY = BinanceFixture.BTC_ETH_SOL_REGISTRY;

    public static RestFixture positionModeChangeSuccess(boolean positionMode) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.POSITION_MODE_PATH_URL)
                        .params(Map.of(
                                "dualSidePosition", positionMode
                        ))
                        .throwError(false)
                        .authRequired(true)
                        .build(),
                ok("""
                {
                    "code": 200,
                    "msg": "success"
                }
                """));
    }

    public static RestFixture positionModeNoNeedToChange(boolean positionMode) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.POSITION_MODE_PATH_URL)
                        .params(Map.of(
                                "dualSidePosition", positionMode
                        ))
                        .authRequired(true)
                        .throwError(false)
                        .build(),
                badRequest("""
                {
                    "code": -4059,
                    "msg": "No need to change position side."
                }
                """));
    }

    public static RestFixture leverageChangeSuccess(String exchangeSymbol, int leverage, int maxNotionalValue) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.LEVERAGE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "leverage", leverage
                        ))
                        .authRequired(true)
                        .build(),

                ok("""
                    {
                        "symbol": "%s",
                        "leverage": %d,
                        "maxNotionalValue": "%s"
                    }
                    """.formatted(exchangeSymbol, leverage, maxNotionalValue)));
    }

    public static RestFixture leverageChangeFailure(String exchangeSymbol, int invalidLeverage) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.LEVERAGE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "leverage", invalidLeverage
                        ))
                        .authRequired(true)
                        .build(),
                badRequest("""
                {
                    "code": -4028,
                    "msg": "Leverage %d is not valid"
                }
                """.formatted(invalidLeverage)));
    }

    public static RestFixture marginModeChangeSuccess(String exchangeSymbol, String marginMode) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.MARGIN_TYPE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "marginType", marginMode
                        ))
                        .authRequired(true)
                        .throwError(false)
                        .build(),
                ok("""
                {
                    "code": 200,
                    "msg": "success"
                }
                """));
    }

    public static RestFixture marginModeNoNeedToChange(String exchangeSymbol, String marginMode) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.MARGIN_TYPE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "marginType", marginMode
                        ))
                        .authRequired(true)
                        .throwError(false)
                        .build(),
                badRequest("""
                {
                    "code": -4046,
                    "msg": "No need to change margin type."
                }
                """));
    }

    public static RestFixture marginModeInvalidSymbol(String exchangeSymbol, String marginMode) {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.MARGIN_TYPE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "marginType", marginMode
                        ))
                        .authRequired(true)
                        .throwError(false)
                        .build(),
                badRequest("""
                {
                    "code": -1121,
                    "msg": "Invalid symbol."
                }
                """));
    }


    /**
     * GET /fapi/v1/premiumIndex?symbol={exchangeSymbol} 성공 응답
     */
    public static RestFixture premiumIndexSuccess(String exchangeSymbol) {
        String body = """
                {
                    "symbol": "%s",
                    "markPrice": "81524.41916667",
                    "indexPrice": "81573.64521739",
                    "estimatedSettlePrice": "81510.11213563",
                    "lastFundingRate": "0.00007587",
                    "interestRate": "0.00010000",
                    "nextFundingTime": 1778832000000,
                    "time": 1778807073000
                }
                """.formatted(exchangeSymbol);

        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.MARK_PRICE_PATH_URL)
                        .params(Map.of("symbol", exchangeSymbol))
                        .build(),
                ok(body)
        );
    }

    /**
     * GET /fapi/v1/premiumIndex 잘못된 심볼 응답 (-1121)
     */
    public static RestFixture premiumIndexInvalidSymbol(String exchangeSymbol) {
        String body = """
                {
                    "code": -1121,
                    "msg": "Invalid symbol."
                }
                """;

        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.MARK_PRICE_PATH_URL)
                        .params(Map.of("symbol", exchangeSymbol))
                        .build(),
                badRequest(body)
        );
    }

    /**
     * GET /fapi/v1/fundingInfo 성공 응답.
     *
     * Binance는 기본 인터벌(8시간)이 아닌 심볼들만 이 엔드포인트에서 반환한다.
     *
     * @param exchangeSymbolToHours exchange symbol(예: "BTCUSDT") → fundingIntervalHours 매핑
     */
    public static RestFixture fundingInfoIntervalSuccess(Map<String, Integer> exchangeSymbolToHours) {
        String entries = exchangeSymbolToHours.entrySet().stream()
                .map(e -> """
                    {
                        "symbol": "%s",
                        "adjustedFundingRateCap": "0.03000000",
                        "adjustedFundingRateFloor": "-0.03000000",
                        "fundingIntervalHours": %d,
                        "disclaimer": false
                    }
                    """.formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(","));
        String body = "[" + entries + "]";
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.FUNDING_INFO_PATH_URL)
                        .build(),
                ok(body)
        );
    }

    /**
     * GET /fapi/v3/account 성공 응답 (single-asset mode).
     * 실제 응답 형태 그대로 — 0 잔고 자산(FDUSD, BNB, ETH 등)도 포함.
     */
    public static RestFixture accountSuccess() {
        String body = """
            {
                "totalInitialMargin": "0.00000000",
                "totalMaintMargin": "0.00000000",
                "totalWalletBalance": "5000.00000000",
                "totalUnrealizedProfit": "0.00000000",
                "totalMarginBalance": "5000.00000000",
                "totalPositionInitialMargin": "0.00000000",
                "totalOpenOrderInitialMargin": "0.00000000",
                "totalCrossWalletBalance": "5000.00000000",
                "totalCrossUnPnl": "0.00000000",
                "availableBalance": "5000.00000000",
                "maxWithdrawAmount": "5000.00000000",
                "assets": [
                    {
                        "asset": "FDUSD",
                        "walletBalance": "0.00000000",
                        "unrealizedProfit": "0.00000000",
                        "marginBalance": "0.00000000",
                        "maintMargin": "0.00000000",
                        "initialMargin": "0.00000000",
                        "positionInitialMargin": "0.00000000",
                        "openOrderInitialMargin": "0.00000000",
                        "crossWalletBalance": "0.00000000",
                        "crossUnPnl": "0.00000000",
                        "availableBalance": "0.00000000",
                        "maxWithdrawAmount": "0.00000000",
                        "updateTime": 0
                    },
                    {
                        "asset": "BNB",
                        "walletBalance": "0.00000000",
                        "unrealizedProfit": "0.00000000",
                        "marginBalance": "0.00000000",
                        "maintMargin": "0.00000000",
                        "initialMargin": "0.00000000",
                        "positionInitialMargin": "0.00000000",
                        "openOrderInitialMargin": "0.00000000",
                        "crossWalletBalance": "0.00000000",
                        "crossUnPnl": "0.00000000",
                        "availableBalance": "0.00000000",
                        "maxWithdrawAmount": "0.00000000",
                        "updateTime": 0
                    },
                    {
                        "asset": "ETH",
                        "walletBalance": "0.00000000",
                        "unrealizedProfit": "0.00000000",
                        "marginBalance": "0.00000000",
                        "maintMargin": "0.00000000",
                        "initialMargin": "0.00000000",
                        "positionInitialMargin": "0.00000000",
                        "openOrderInitialMargin": "0.00000000",
                        "crossWalletBalance": "0.00000000",
                        "crossUnPnl": "0.00000000",
                        "availableBalance": "0.00000000",
                        "maxWithdrawAmount": "0.00000000",
                        "updateTime": 0
                    },
                    {
                        "asset": "BTC",
                        "walletBalance": "0.01000000",
                        "unrealizedProfit": "0.00000000",
                        "marginBalance": "0.01000000",
                        "maintMargin": "0.00000000",
                        "initialMargin": "0.00000000",
                        "positionInitialMargin": "0.00000000",
                        "openOrderInitialMargin": "0.00000000",
                        "crossWalletBalance": "0.01000000",
                        "crossUnPnl": "0.00000000",
                        "availableBalance": "0.01000000",
                        "maxWithdrawAmount": "0.01000000",
                        "updateTime": 1778546240538
                    },
                    {
                        "asset": "USDT",
                        "walletBalance": "5000.00000000",
                        "unrealizedProfit": "0.00000000",
                        "marginBalance": "5000.00000000",
                        "maintMargin": "0.00000000",
                        "initialMargin": "0.00000000",
                        "positionInitialMargin": "0.00000000",
                        "openOrderInitialMargin": "0.00000000",
                        "crossWalletBalance": "5000.00000000",
                        "crossUnPnl": "0.00000000",
                        "availableBalance": "5000.00000000",
                        "maxWithdrawAmount": "5000.00000000",
                        "updateTime": 1778546240488
                    },
                    {
                        "asset": "USDC",
                        "walletBalance": "5000.00000000",
                        "unrealizedProfit": "0.00000000",
                        "marginBalance": "5000.00000000",
                        "maintMargin": "0.00000000",
                        "initialMargin": "0.00000000",
                        "positionInitialMargin": "0.00000000",
                        "openOrderInitialMargin": "0.00000000",
                        "crossWalletBalance": "5000.00000000",
                        "crossUnPnl": "0.00000000",
                        "availableBalance": "5000.00000000",
                        "maxWithdrawAmount": "5000.00000000",
                        "updateTime": 1778546240513
                    }
                ],
                "positions": []
            }
            """;

        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .pathUrl(ApiSpec.ACCOUNT_PATH_URL)
                        .build(),
                ok(body)
        );
    }

    public static final String LISTEN_KEY = "test-listen-key";

    public static RestFixture listenKeyCreateSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.LISTEN_KEY_PATH_URL)
                        .authRequired(true)
                        .build(),
                ok("""
                {
                    "listenKey": "%s"
                }
                """.formatted(LISTEN_KEY))
        );
    }

    public static RestFixture listenKeyKeepAliveSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.PUT)
                        .pathUrl(ApiSpec.LISTEN_KEY_PATH_URL)
                        .authRequired(true)
                        .build(),
                ok("{}")
        );
    }

    public static final String TRADE_CLIENT_ORDER_ID = "client-order-1";
    public static final String TRADE_EXCHANGE_ORDER_ID = "123456789";
    public static final String TRADE_EXCHANGE_SYMBOL = "BTCUSDT";
    public static final String TRADE_TRADING_PAIR = "BTC-USDT";
    public static final String USER_TRADE_ID_1 = "10001";
    public static final String USER_TRADE_ID_2 = "10002";
    public static final Instant USER_TRADE_TIME_1 = Instant.ofEpochMilli(1779849678000L);
    public static final Instant USER_TRADE_TIME_2 = Instant.ofEpochMilli(1779849679000L);
    public static final BigDecimal USER_TRADE_PRICE_1 = new BigDecimal("70000.10");
    public static final BigDecimal USER_TRADE_PRICE_2 = new BigDecimal("70001.20");
    public static final BigDecimal USER_TRADE_BASE_AMOUNT_1 = new BigDecimal("0.001");
    public static final BigDecimal USER_TRADE_BASE_AMOUNT_2 = new BigDecimal("0.002");
    public static final BigDecimal USER_TRADE_QUOTE_AMOUNT_1 = new BigDecimal("70.00010");
    public static final BigDecimal USER_TRADE_QUOTE_AMOUNT_2 = new BigDecimal("140.00240");
    public static final BigDecimal USER_TRADE_FEE_1 = new BigDecimal("0.03500005");
    public static final BigDecimal USER_TRADE_FEE_2 = new BigDecimal("0.07000120");
    public static final String USER_TRADE_FEE_ASSET = "USDT";

    public static RestFixture userTradesSuccess(String exchangeSymbol, String exchangeOrderId) {
        String body = """
                [
                    {
                        "id": %s,
                        "orderId": %s,
                        "price": "%s",
                        "qty": "%s",
                        "quoteQty": "%s",
                        "commission": "%s",
                        "commissionAsset": "%s",
                        "time": %d,
                        "maker": false
                    },
                    {
                        "id": %s,
                        "orderId": %s,
                        "price": "%s",
                        "qty": "%s",
                        "quoteQty": "%s",
                        "commission": "%s",
                        "commissionAsset": "%s",
                        "time": %d,
                        "maker": true
                    }
                ]
                """.formatted(
                USER_TRADE_ID_1,
                exchangeOrderId,
                USER_TRADE_PRICE_1.toPlainString(),
                USER_TRADE_BASE_AMOUNT_1.toPlainString(),
                USER_TRADE_QUOTE_AMOUNT_1.toPlainString(),
                USER_TRADE_FEE_1.toPlainString(),
                USER_TRADE_FEE_ASSET,
                USER_TRADE_TIME_1.toEpochMilli(),
                USER_TRADE_ID_2,
                exchangeOrderId,
                USER_TRADE_PRICE_2.toPlainString(),
                USER_TRADE_BASE_AMOUNT_2.toPlainString(),
                USER_TRADE_QUOTE_AMOUNT_2.toPlainString(),
                USER_TRADE_FEE_2.toPlainString(),
                USER_TRADE_FEE_ASSET,
                USER_TRADE_TIME_2.toEpochMilli()
        );
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.TRADE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "orderId", exchangeOrderId
                        ))
                        .authRequired(true)
                        .build(),
                ok(body)
        );
    }

    public static final Instant USER_STREAM_EVENT_TIME = Instant.ofEpochMilli(1779849680000L);

    public static WsResponse orderTradeUpdateFilled() {
        String body = """
                {
                    "e": "ORDER_TRADE_UPDATE",
                    "T": %d,
                    "o": {
                        "s": "%s",
                        "c": "%s",
                        "i": %s,
                        "X": "FILLED",
                        "t": %s,
                        "T": %d,
                        "L": "%s",
                        "l": "%s",
                        "N": "%s",
                        "n": "%s",
                        "m": true
                    }
                }
                """.formatted(
                USER_STREAM_EVENT_TIME.toEpochMilli(),
                TRADE_EXCHANGE_SYMBOL,
                TRADE_CLIENT_ORDER_ID,
                TRADE_EXCHANGE_ORDER_ID,
                USER_TRADE_ID_1,
                USER_TRADE_TIME_1.toEpochMilli(),
                USER_TRADE_PRICE_1.toPlainString(),
                USER_TRADE_BASE_AMOUNT_1.toPlainString(),
                USER_TRADE_FEE_ASSET,
                USER_TRADE_FEE_1.toPlainString()
        );
        return new WsResponse(body, WsResponse.MessageType.TEXT);
    }

    public static WsResponse orderTradeUpdateWithoutFill() {
        String body = """
                {
                    "e": "ORDER_TRADE_UPDATE",
                    "T": %d,
                    "o": {
                        "s": "%s",
                        "c": "%s",
                        "i": %s,
                        "X": "NEW",
                        "t": 0,
                        "T": %d,
                        "L": "0",
                        "l": "0",
                        "N": "0",
                        "n": "0",
                        "m": false
                    }
                }
                """.formatted(
                USER_STREAM_EVENT_TIME.toEpochMilli(),
                TRADE_EXCHANGE_SYMBOL,
                TRADE_CLIENT_ORDER_ID,
                TRADE_EXCHANGE_ORDER_ID,
                USER_TRADE_TIME_1.toEpochMilli()
        );
        return new WsResponse(body, WsResponse.MessageType.TEXT);
    }

    public static final Instant ACCOUNT_UPDATE_TIME = Instant.ofEpochMilli(1779849690000L);
    public static final BigDecimal POSITION_AMOUNT = new BigDecimal("-0.010");
    public static final BigDecimal POSITION_ENTRY_PRICE = new BigDecimal("70000.00");
    public static final BigDecimal POSITION_UNREALIZED_PNL = new BigDecimal("-12.34");

    public static WsResponse accountUpdateWithPosition() {
        String body = """
                {
                    "e": "ACCOUNT_UPDATE",
                    "T": %d,
                    "a": {
                        "m": "ORDER",
                        "B": [],
                        "P": [
                            {
                                "s": "%s",
                                "pa": "%s",
                                "ep": "%s",
                                "up": "%s",
                                "ps": "SHORT"
                            }
                        ]
                    }
                }
                """.formatted(
                ACCOUNT_UPDATE_TIME.toEpochMilli(),
                TRADE_EXCHANGE_SYMBOL,
                POSITION_AMOUNT.toPlainString(),
                POSITION_ENTRY_PRICE.toPlainString(),
                POSITION_UNREALIZED_PNL.toPlainString()
        );
        return new WsResponse(body, WsResponse.MessageType.TEXT);
    }

    public static final BigDecimal DIFF_BID_PRICE = new BigDecimal("25216.70");
    public static final BigDecimal DIFF_BID_QTY   = new BigDecimal("0.816");
    public static final BigDecimal DIFF_ASK_PRICE = new BigDecimal("75716.80");
    public static final BigDecimal DIFF_ASK_QTY   = new BigDecimal("5.930");
    public static final long DIFF_FIRST_UPDATE_ID = 10638869595158L;  // U
    public static final long DIFF_LAST_UPDATE_ID  = 10638869608903L;  // u
    public static final long DIFF_PREV_UPDATE_ID  = 10638869595036L;  // pu
    public static final Instant DIFF_EVENT_TIME   = Instant.ofEpochMilli(1779849678696L);

    public static final List<OrderBookEntry> DIFF_BIDS = List.of(new OrderBookEntry(0L, DIFF_BID_PRICE, DIFF_BID_QTY));
    public static final List<OrderBookEntry> DIFF_ASKS = List.of(new OrderBookEntry(0L, DIFF_ASK_PRICE, DIFF_ASK_QTY));

    /** WS depthUpdate raw stream 메시지. */
    public static WsResponse wsDepthUpdate(String exchangeSymbol) {
        String json = """
            {
                "e": "depthUpdate",
                "E": %d,
                "T": 1779849678694,
                "s": "%s",
                "U": %d,
                "u": %d,
                "pu": %d,
                "b": [
                    ["%s", "%s"]
                ],
                "a": [
                    ["%s", "%s"]
                ]
            }
            """.formatted(
                DIFF_EVENT_TIME.toEpochMilli(),
                exchangeSymbol,
                DIFF_FIRST_UPDATE_ID,
                DIFF_LAST_UPDATE_ID,
                DIFF_PREV_UPDATE_ID,
                DIFF_BID_PRICE.toPlainString(), DIFF_BID_QTY.toPlainString(),
                DIFF_ASK_PRICE.toPlainString(), DIFF_ASK_QTY.toPlainString()
        );
        return new WsResponse(json, WsResponse.MessageType.TEXT);
    }

    // ===== OrderBook WS: aggTrade =====
    public static final long TRADE_ID = 12345L;
    public static final BigDecimal TRADE_PRICE = new BigDecimal("75716.80");
    public static final BigDecimal TRADE_QTY   = new BigDecimal("0.150");
    public static final Instant TRADE_TIME = Instant.ofEpochMilli(1779849678694L);

    /** WS aggTrade raw stream 메시지. m=false → BUY(taker가 매수). */
    public static WsResponse wsAggTrade(String exchangeSymbol) {
        String json = """
            {
                "e": "aggTrade",
                "E": %d,
                "s": "%s",
                "a": %d,
                "p": "%s",
                "q": "%s",
                "T": %d,
                "m": false
            }
            """.formatted(
                TRADE_TIME.toEpochMilli(),
                exchangeSymbol,
                TRADE_ID,
                TRADE_PRICE.toPlainString(),
                TRADE_QTY.toPlainString(),
                TRADE_TIME.toEpochMilli()
        );
        return new WsResponse(json, WsResponse.MessageType.TEXT);
    }

    // ===== OrderBook WS: ack / error =====
    public static WsResponse wsAckResponse(int id) {
        return new WsResponse(
                """
                {"result": null, "id": %d}
                """.formatted(id),
                WsResponse.MessageType.TEXT);
    }

    public static WsResponse wsErrorResponse(int code, String msg) {
        return new WsResponse(
                """
                {"error": {"code": %d, "msg": "%s"}, "id": 1}
                """.formatted(code, msg),
                WsResponse.MessageType.TEXT);
    }

    // ===== OrderBook REST: depth 스냅샷 =====
    public static final long SNAPSHOT_UPDATE_ID = 1027024L;
    public static final Instant SNAPSHOT_EVENT_TIME = Instant.ofEpochMilli(1589436922959L);  // T (ms)
    public static final BigDecimal SNAPSHOT_BID_PRICE = new BigDecimal("4.00000000");
    public static final BigDecimal SNAPSHOT_BID_QTY   = new BigDecimal("431.00000000");
    public static final BigDecimal SNAPSHOT_ASK_PRICE = new BigDecimal("4.00000200");
    public static final BigDecimal SNAPSHOT_ASK_QTY   = new BigDecimal("12.00000000");

    public static final List<OrderBookEntry> SNAPSHOT_BIDS = List.of(
            new OrderBookEntry(0L, SNAPSHOT_BID_PRICE, SNAPSHOT_BID_QTY));
    public static final List<OrderBookEntry> SNAPSHOT_ASKS = List.of(
            new OrderBookEntry(0L, SNAPSHOT_ASK_PRICE, SNAPSHOT_ASK_QTY));

    /** GET depth (REST 스냅샷) 성공 응답. */
    public static RestFixture depthSnapshotSuccess(String exchangeSymbol) {
        String body = """
            {
                "lastUpdateId": %d,
                "E": 1589436922972,
                "T": %d,
                "bids": [
                    ["%s", "%s"]
                ],
                "asks": [
                    ["%s", "%s"]
                ]
            }
            """.formatted(
                SNAPSHOT_UPDATE_ID,
                SNAPSHOT_EVENT_TIME.toEpochMilli(),
                SNAPSHOT_BID_PRICE.toPlainString(), SNAPSHOT_BID_QTY.toPlainString(),
                SNAPSHOT_ASK_PRICE.toPlainString(), SNAPSHOT_ASK_QTY.toPlainString()
        );
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(ApiSpec.SNAPSHOT_PATH_URL)
                        .params(Map.of("symbol", exchangeSymbol, "limit", "1000"))
                        .build(),
                ok(body)
        );
    }

    public static final BigDecimal MARK_PRICE_VALUE = new BigDecimal("81524.41916667");
    public static final BigDecimal MARK_PRICE_FUNDING_RATE = new BigDecimal("0.00007587");
    public static final Instant MARK_PRICE_EVENT_TIME = Instant.ofEpochMilli(1778807073000L);
    public static final Instant MARK_PRICE_NEXT_FUNDING_TIME = Instant.ofEpochMilli(1778832000000L);

    public static WsResponse wsMarkPriceMessage(String exchangeSymbol) {
        String json = """
                {
                    "e": "markPriceUpdate",
                    "E": %d,
                    "s": "%s",
                    "p": "%s",
                    "P": "0",
                    "r": "%s",
                    "T": %d
                }
                """.formatted(
                MARK_PRICE_EVENT_TIME.toEpochMilli(),
                exchangeSymbol,
                MARK_PRICE_VALUE.toPlainString(),
                MARK_PRICE_FUNDING_RATE.toPlainString(),
                MARK_PRICE_NEXT_FUNDING_TIME.toEpochMilli()
        );
        return new WsResponse(json, WsResponse.MessageType.TEXT);
    }

    public static WsResponse ackResponse(int id) {
        return new WsResponse(
                """
                {"id": %d, "result": null}
                """.formatted(id),
                WsResponse.MessageType.TEXT
                );
    }

    public static WsResponse restErrorResponse(int code, String msg) {
        return new WsResponse("""
                {
                    "id": 1,
                    "error": {
                        "code": %d,
                        "msg": "%s"
                    }
                }
                """.formatted(code, msg),
                WsResponse.MessageType.TEXT
        );
    }
}
