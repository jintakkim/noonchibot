package com.hotak.noonchibot.connector.derivative.bybit;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.TimeInForce;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BybitDerivativeApiSpec {
    public static final String ORDER_ID_PREFIX = "";
    public static final int MAX_ORDER_ID_LENGTH = 32;

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

    private static final int MAX_PUBLIC_REQUEST_PER_SEC = 120;
    private static final int MAX_PRIVATE_REQUEST_PER_SEC = 10;
    private static final int DEFAULT_WEIGHT = 1;

    public static final List<RateLimit> RATE_LIMITS = List.of(
            RateLimit.pool("PUBLIC_IP", MAX_PUBLIC_REQUEST_PER_SEC, Duration.ofSeconds(1)),
            RateLimit.pool("PRIVATE_UID", MAX_PRIVATE_REQUEST_PER_SEC, Duration.ofSeconds(1)),

            RateLimit.endpoint(TICKER_PRICE_CHANGE_PATH_URL, Duration.ofSeconds(1), MAX_PUBLIC_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PUBLIC_IP", 1))),

            RateLimit.endpoint(SNAPSHOT_PATH_URL, Duration.ofSeconds(1), MAX_PUBLIC_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PUBLIC_IP", 1))),

            RateLimit.endpoint(SERVER_TIME_PATH_URL, Duration.ofSeconds(1), MAX_PUBLIC_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PUBLIC_IP", 1))),

            RateLimit.endpoint(EXCHANGE_INFO_PATH_URL, Duration.ofSeconds(1), MAX_PUBLIC_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PUBLIC_IP", 1))),

            RateLimit.endpoint(ORDER_CREATE_PATH_URL, Duration.ofSeconds(1), MAX_PRIVATE_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PRIVATE_UID", 1))),

            RateLimit.endpoint(ORDER_CANCEL_PATH_URL, Duration.ofSeconds(1), MAX_PRIVATE_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PRIVATE_UID", 1))),

            RateLimit.endpoint(ORDER_REALTIME_PATH_URL, Duration.ofSeconds(1), MAX_PRIVATE_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PRIVATE_UID", 1))),

            RateLimit.endpoint(ACCOUNTS_PATH_URL, Duration.ofSeconds(1), MAX_PRIVATE_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PRIVATE_UID", 1))),

            RateLimit.endpoint(MY_TRADES_PATH_URL, Duration.ofSeconds(1), MAX_PRIVATE_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PRIVATE_UID", 1))),

            RateLimit.endpoint(COMMISSION_RATE_PATH_URL, Duration.ofSeconds(1), MAX_PRIVATE_REQUEST_PER_SEC, DEFAULT_WEIGHT,
                    List.of(new RateLimit.LinkedLimitWeightPair("PRIVATE_UID", 1)))
    );

    public static final int ORDER_NOT_EXIST_ERROR_CODE = 110001;
    public static final String ORDER_NOT_EXIST_MESSAGE = "Order does not exist";
    public static final int INSUFFICIENT_BALANCE_ERROR_CODE = 110007;
    public static final String INSUFFICIENT_BALANCE_MESSAGE = "ab not enough for new order";
    public static final int TIMESTAMP_ERROR_CODE = 10002;
    public static final String TIMESTAMP_ERROR_MESSAGE = "Request expired or timestamp invalid";

    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);
}