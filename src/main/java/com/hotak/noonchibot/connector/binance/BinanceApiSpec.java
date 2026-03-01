package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.connector.throttle.RateLimitPool;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

import static com.hotak.noonchibot.connector.throttle.RateLimit.pool;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BinanceApiSpec {
    public static final String REST_BASE_URL = "https://api.binance.com/api";
    public static final String WSS_URL = "wss://stream.binance.com:9443/ws";
    public static final String PUBLIC_API_VERSION = "/v3";
    public static final String PRIVATE_API_VERSION = "/v3";
    public static final String TICKER_PRICE_CHANGE_PATH_URL = "/ticker/24hr";
    public static final String SNAPSHOT_PATH_URL = "/depth";
    public static final String SERVER_TIME_PATH_URL = "/time";


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
                    ))
    );
}
