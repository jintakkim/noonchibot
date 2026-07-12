package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.TimeInForce;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiSpec {
    public static final String ORDER_ID_PREFIX = "";
    public static final int MAX_ORDER_ID_LENGTH = 32;

    public static final String PLATFORM_NAME = "binanceFuture";
    public static final String REST_BASE_URL = "https://fapi.binance.com/fapi";
    public static final String TESTNET_REST_BASE_URL = "https://testnet.binancefuture.com/fapi";
    public static final String WSS_PUBLIC_URL = "wss://fstream.binance.com/public/ws";
    public static final String TESTNET_WSS_PUBLIC_URL = "wss://stream.binancefuture.com/ws";
    public static final String WSS_MARKET_URL = "wss://market.binance.com/market/ws";
    public static final String TESTNET_WSS_MARKET_URL = "wss://stream.binancefuture.com/ws";
    public static final String WSS_PRIVATE_URL = "wss://fstream.binance.com/ws";
    public static final String TESTNET_WSS_PRIVATE_URL = "wss://stream.binancefuture.com/ws";

    public static final String SERVER_TIME_PATH_URL = "/v1/time";
    public static final String MARK_PRICE_PATH_URL = "/v1/premiumIndex";
    public static final String TICKER_PRICE_CHANGE_PATH_URL = "/v1/ticker/24hr";
    public static final String SNAPSHOT_PATH_URL = "/v1/depth";
    public static final String EXCHANGE_INFO_PATH_URL = "/v1/exchangeInfo";
    public static final String ORDER_PATH_URL = "/v1/order";
    public static final String LISTEN_KEY_PATH_URL = "/v1/listenKey";
    public static final String ACCOUNT_PATH_URL = "/v3/account";
    public static final String TRADE_PATH_URL = "/v1/userTrades";
    public static final String COMMISSION_RATE_PATH_URL = "/v1/commissionRate";
    public static final String FUNDING_INFO_PATH_URL = "/v1/fundingInfo";
    public static final String FUNDING_RATE_HISTORY_PATH_URL = "/v1/fundingRate";
    public static final String KLINE_PATH_URL = "/v1/klines";
    public static final String POSITION_MODE_PATH_URL = "/fapi/v1/positionSide/dual";
    public static final String LEVERAGE_PATH_URL = "/fapi/v1/leverage";
    public static final String MARGIN_TYPE_PATH_URL = "/fapi/v1/marginType";

    public static final String WS_SUBSCRIBE = "SUBSCRIBE";
    public static final String WS_UNSUBSCRIBE = "UNSUBSCRIBE";


    public static final int MAX_REQUEST = 2400;
    public static final int NOT_USED = 1;

    public static final Duration DEFAULT_FUNDING_INTERVAL = Duration.ofHours(8);

    public static final Set<TimeInForce> SUPPORTED_TIME_IN_FORCE = Set.of(TimeInForce.FOK, TimeInForce.IOC, TimeInForce.GTC);

    public static final Map<TimeInForce, String> TIME_IN_FORCE_API_VALUE = Map.of(
            TimeInForce.FOK, "FOK",
            TimeInForce.IOC, "IOC",
            TimeInForce.GTC, "GTC"
    );

    public static final Map<String, OrderState> ORDER_STATE = Map.of(
            "NEW", OrderState.OPEN,
            "FILLED", OrderState.FILLED,
            "PARTIALLY_FILLED", OrderState.PARTIALLY_FILLED,
            "CANCELED", OrderState.CANCELED,
            "EXPIRED", OrderState.FAILED,
            "EXPIRED_IN_MATCH", OrderState.FAILED
    );

    public static final List<RateLimit> RATE_LIMITS = List.of(
            // Pools
            RateLimit.pool("REQUEST_WEIGHT", 2400, Duration.ofMinutes(1)),
            RateLimit.pool("ORDERS_1MIN", 1200, Duration.ofMinutes(1)),
            RateLimit.pool("ORDERS_10SEC", 300, Duration.ofSeconds(10)),

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
                    )),
            RateLimit.endpoint(ORDER_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
                            new RateLimit.LinkedLimitWeightPair("ORDERS_1MIN", 1),
                            new RateLimit.LinkedLimitWeightPair("ORDERS_10SEC", 1)
                    )),
            RateLimit.endpoint(LISTEN_KEY_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(ACCOUNT_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 5)
                    )),
            RateLimit.endpoint(TRADE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 5)
                    )),
            RateLimit.endpoint(COMMISSION_RATE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 20)
                    )),
            RateLimit.endpoint(FUNDING_INFO_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 0)
                    )),
            RateLimit.endpoint(FUNDING_RATE_HISTORY_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(KLINE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 5)
                    )),
            RateLimit.endpoint(POSITION_MODE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(LEVERAGE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    )),
            RateLimit.endpoint(MARGIN_TYPE_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1)
                    ))
    );

    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);

    public static class Code {
        public static final int SUCCESS = 200;
        public static final int UNKNOWN_ORDER_DURING_CANCELLATION_ERROR = -2011;
        public static final int TIMESTAMP_ERROR = -1021;

        public static final int ORDER_NOT_EXIST_ERROR = -2013;

        public static final int NO_NEED_TO_CHANGE_POSITION_SIDE = -4059;
        public static final int POSITION_SIDE_CHANGE_EXISTS_OPEN_ORDERS = -4067;
        public static final int POSITION_SIDE_CHANGE_EXISTS_QUANTITY = -4068;
        public static final int NO_NEED_TO_CHANGE_MARGIN_TYPE = -4046;


    }
}
