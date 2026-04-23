package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.TimeInForce;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DerivativeApiSpec {
    public static final String ORDER_ID_PREFIX = "";
    public static final int MAX_ORDER_ID_LENGTH = 36;

    public static final String PLATFORM_NAME = "bybitFuture";

    public static final String REST_BASE_URL = "https://api.bybit.com";
    public static final String WSS_INVERSE_URL = "wss://stream.bybit.com/v5/public/inverse";
    public static final String WSS_LINEAR_URL = "wss://stream.bybit.com/v5/public/linear";
    public static final String WSS_API_URL = "wss://stream.bybit.com/v5/private";

    public static final String TICKER_PRICE_CHANGE_PATH_URL = "/v5/market/tickers";
    public static final String SNAPSHOT_PATH_URL = "/v5/market/orderbook";
    public static final String SERVER_TIME_PATH_URL = "/v5/market/time";
    public static final String EXCHANGE_INFO_PATH_URL = "/v5/market/instruments-info";
    public static final String ORDER_CREATE_PATH_URL = "/v5/order/create";
    public static final String ORDER_CANCEL_PATH_URL = "/v5/order/cancel";
    public static final String ORDER_REALTIME_PATH_URL = "/v5/order/realtime";
    public static final String ACCOUNTS_PATH_URL = "/v5/account/wallet-balance";
    public static final String MY_TRADES_PATH_URL = "/v5/execution/list";
    public static final String COMMISSION_RATE_PATH_URL = "/v5/account/fee-rate";

    public static final Map<TimeInForce, String> TIME_IN_FORCE_API_VALUE = Map.of(
            TimeInForce.FOK, "FOK",
            TimeInForce.IOC, "IOC",
            TimeInForce.GTC, "GTC"
    );

    public static final Map<String, OrderState> ORDER_STATE = Map.of(
            "New", OrderState.OPEN,
            "Untriggered", OrderState.OPEN,
            "Triggered", OrderState.OPEN,
            "Filled", OrderState.FILLED,
            "PartiallyFilled", OrderState.PARTIALLY_FILLED,
            "Cancelled", OrderState.CANCELED,
            "PartiallyFilledCanceled", OrderState.CANCELED,
            "Rejected", OrderState.FAILED,
            "Deactivated", OrderState.CANCELED
    );

    private static final int UNLIMITED = Integer.MAX_VALUE;
    private static final int DEFAULT_WEIGHT = 1;

    public static final List<RateLimit> RATE_LIMITS = List.of(
            RateLimit.pool("IP_TOTAL", 600, Duration.ofSeconds(5)),

            RateLimit.pool("UID_ORDER_CREATE", 10, Duration.ofSeconds(1)),
            RateLimit.pool("UID_ORDER_CANCEL", 10, Duration.ofSeconds(1)),
            RateLimit.pool("UID_ORDER_REALTIME", 50, Duration.ofSeconds(1)),
            RateLimit.pool("UID_WALLET_BALANCE", 50, Duration.ofSeconds(1)),
            RateLimit.pool("UID_EXECUTION_LIST", 50, Duration.ofSeconds(1)),
            RateLimit.pool("UID_FEE_RATE", 10, Duration.ofSeconds(1)),

            // 공개 엔드포인트: IP 풀만 공유, 엔드포인트 자체 limit 없음
            RateLimit.endpoint(TICKER_PRICE_CHANGE_PATH_URL, Duration.ofSeconds(5), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1))),
            RateLimit.endpoint(SNAPSHOT_PATH_URL, Duration.ofSeconds(5), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1))),
            RateLimit.endpoint(SERVER_TIME_PATH_URL, Duration.ofSeconds(5), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1))),
            RateLimit.endpoint(EXCHANGE_INFO_PATH_URL, Duration.ofSeconds(5), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1))),

            RateLimit.endpoint(ORDER_CREATE_PATH_URL, Duration.ofSeconds(1), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1),
                            new RateLimit.LinkedLimitWeightPair("UID_ORDER_CREATE", 1)
                    )),
            RateLimit.endpoint(ORDER_CANCEL_PATH_URL, Duration.ofSeconds(1), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1),
                            new RateLimit.LinkedLimitWeightPair("UID_ORDER_CANCEL", 1)
                    )),
            RateLimit.endpoint(ORDER_REALTIME_PATH_URL, Duration.ofSeconds(1), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1),
                            new RateLimit.LinkedLimitWeightPair("UID_ORDER_REALTIME", 1)
                    )),
            RateLimit.endpoint(ACCOUNTS_PATH_URL, Duration.ofSeconds(1), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1),
                            new RateLimit.LinkedLimitWeightPair("UID_WALLET_BALANCE", 1)
                    )),
            RateLimit.endpoint(MY_TRADES_PATH_URL, Duration.ofSeconds(1), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1),
                            new RateLimit.LinkedLimitWeightPair("UID_EXECUTION_LIST", 1)
                    )),
            RateLimit.endpoint(COMMISSION_RATE_PATH_URL, Duration.ofSeconds(1), UNLIMITED, DEFAULT_WEIGHT,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("IP_TOTAL", 1),
                            new RateLimit.LinkedLimitWeightPair("UID_FEE_RATE", 1)
                    ))
    );

    public static final int ORDER_NOT_EXIST_ERROR_CODE = 110001;
    public static final int UNKNOWN_ORDER_ERROR_CODE = 110007;
    public static final int TIMESTAMP_ERROR_CODE = 10002;

    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);
}