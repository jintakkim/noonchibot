package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BinanceDerivativeApiSpec {
    public static final String REST_BASE_URL = "https://fapi.binance.com/fapi/";
    public static final String WSS_PUBLIC_URL = "wss://fstream.binance.com/stream";
    public static final String WSS_PRIVATE_URL = "wss://fstream.binance.com/ws";

    public static final String SERVER_TIME_PATH_URL = "v1/time";
    public static final String MARK_PRICE_PATH_URL = "v1/premiumIndex";
    public static final String TICKER_PRICE_CHANGE_PATH_URL = "v1/ticker/24hr";
    public static final String SNAPSHOT_PATH_URL = "v1/depth";
    public static final String EXCHANGE_INFO_PATH_URL = "v1/exchangeInfo";

    public static final int MAX_REQUEST = 2400;
    public static final int NOT_USED = 1;

    public static final List<RateLimit> RATE_LIMITS = List.of(
            // Pools
            RateLimit.pool("REQUEST_WEIGHT", 2400, Duration.ofMinutes(1)),
            RateLimit.pool("ORDERS_1MIN", 1200, Duration.ofMinutes(1)),
            RateLimit.pool("ORDERS_1SEC", 300, Duration.ofSeconds(1)),

            //endpoints
            RateLimit.endpoint(TICKER_PRICE_CHANGE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(SNAPSHOT_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20)
                    )),
            RateLimit.endpoint(SERVER_TIME_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(MARK_PRICE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(EXCHANGE_INFO_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    ))
//            RateLimit.endpoint(ORDER_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(
//                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 4),
//                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1),
//                            new RateLimit.LinkedLimitWeightPair("ORDERS", 1),
//                            new RateLimit.LinkedLimitWeightPair("ORDERS_24HR", 1)
//                    )),
//            RateLimit.endpoint(PING_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(
//                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
//                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
//                    )),
//            RateLimit.endpoint(ACCOUNTS_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(
//                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
//                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
//                    )),
//            RateLimit.endpoint(MY_TRADES_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(
//                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
//                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
//                    )),
//            RateLimit.endpoint(COMMISSION_RATE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(
//                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20),
//                            new RateLimit.LinkedLimitWeightPair("RAW_REQUESTS", 1)
//                    ))
    );

    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);
}
