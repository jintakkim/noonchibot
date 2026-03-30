package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.core.order.InFlightOrder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BinanceApiSpec {
    public static final String ORDER_ID_PREFIX = "";
    public static final int MAX_ORDER_ID_LENGTH = 32;

    public static final String PLATFORM_NAME = "binance";


    public static final String REST_BASE_URL = "https://api.binance.com/api";
    public static final String WSS_URL = "wss://stream.binance.com:9443/ws";
    public static final String WSS_API_URL = "wss://ws-api.binance.com:443/ws-api/v3";
    public static final String TICKER_PRICE_CHANGE_PATH_URL = "/v3/ticker/24hr";
    public static final String SNAPSHOT_PATH_URL = "/v3/depth";
    public static final String SERVER_TIME_PATH_URL = "/v3/time";
    public static final String EXCHANGE_INFO_PATH_URL = "/v3/exchangeInfo";
    public static final String ORDER_PATH_URL = "/v3/inFlightOrder";
    public static final String PING_PATH_URL = "/v3/ping";
    public static final String ACCOUNTS_PATH_URL = "/v3/account";
    public static final String MY_TRADES_PATH_URL = "/v3/myTrades";
    public static final String COMMISSION_RATE_PATH_URL = "/v3/account/commission";

    public static final String TIME_IN_FORCE_GTC = "GTC"; //limit 전용, 캔슬 전까지 주문 만료 없음.

    public static final Map<String, InFlightOrder.State> ORDER_STATE = Map.of(
            "PENDING", InFlightOrder.State.PENDING_CREATE,
            "NEW", InFlightOrder.State.OPEN,
            "FILLED", InFlightOrder.State.FILLED,
            "PARTIALLY_FILLED", InFlightOrder.State.PARTIALLY_FILLED,
            "PENDING_CANCEL", InFlightOrder.State.OPEN,
            "CANCELED", InFlightOrder.State.CANCELED,
            "REJECTED", InFlightOrder.State.FAILED,
            "EXPIRED", InFlightOrder.State.FAILED,
            "EXPIRED_IN_MATCH", InFlightOrder.State.FAILED
    );


    public static int getTickerPriceChangeDynamicWeight(int symbolCount) {
        if (symbolCount == 0) return 80;          // symbols 생략
        if (symbolCount <= 20) return 2;          // 1-20
        if (symbolCount <= 100) return 40;        // 21-100
        return 80;                                 // 101+
    }

    private static final int MAX_REQUEST = 99999;
    private static final int NOT_USED = 1;

    public static final List<RateLimit> RATE_LIMITS = List.of(
            // Pools
            RateLimit.pool("REQUEST_WEIGHT", 6000, Duration.ofMinutes(1)),
            RateLimit.pool("ORDERS", 100, Duration.ofSeconds(10)),
            RateLimit.pool("ORDERS_24HR", 200_000, Duration.ofDays(1)),
            RateLimit.pool("RAW_REQUESTS", 61_000, Duration.ofMinutes(5)),

            //endpoints
            RateLimit.endpoint(TICKER_PRICE_CHANGE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 2),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(SNAPSHOT_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                        new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 100),
                        new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(SERVER_TIME_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(EXCHANGE_INFO_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(ORDER_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 4),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1),
                            new RateLimit.LinkedLimitWeightPair("ORDERS", 1),
                            new RateLimit.LinkedLimitWeightPair("ORDERS_24HR", 1)
                    )),
            RateLimit.endpoint(PING_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(ACCOUNTS_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(MY_TRADES_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    )),
            RateLimit.endpoint(COMMISSION_RATE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
                    ))
    );


    public static final int ORDER_NOT_EXIST_ERROR_CODE = -2013;
    public static final String ORDER_NOT_EXIST_MESSAGE = "InFlightOrder does not exist";
    public static final int UNKNOWN_ORDER_ERROR_CODE = -2011;
    public static final String UNKNOWN_ORDER_MESSAGE = "Unknown inFlightOrder sent";
    public static final int TIMESTAMP_ERROR_CODE = -1021;
    public static final String TIMESTAMP_ERROR_MESSAGE = "Timestamp for this request";

    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);

}
