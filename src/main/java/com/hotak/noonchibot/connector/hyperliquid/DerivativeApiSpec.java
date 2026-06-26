package com.hotak.noonchibot.connector.hyperliquid;

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
final class DerivativeApiSpec {
    public static final String PLATFORM_NAME = "hyperliquidFuture";
    public static final String WS_URL = "wss://api.hyperliquid.xyz/ws";
    public static final String BASE_URL = "https://api.hyperliquid.xyz";
    public static final String TESTNET_BASE_URL = "https://api.hyperliquid-testnet.xyz";

    public static final String INFO_PATH_URL = "/info";
    public static final String EXCHANGE_PATH_URL = "/exchange";

    public static final Map<String, OrderState> ORDER_STATE = Map.ofEntries(
            // 활성 상태
            Map.entry("open", OrderState.OPEN),
            Map.entry("triggered", OrderState.OPEN),

            // 체결
            Map.entry("filled", OrderState.FILLED),

            // 취소 계열
            Map.entry("canceled", OrderState.CANCELED),
            Map.entry("marginCanceled", OrderState.CANCELED),
            Map.entry("vaultWithdrawalCanceled", OrderState.CANCELED),
            Map.entry("openInterestCapCanceled", OrderState.CANCELED),
            Map.entry("selfTradeCanceled", OrderState.CANCELED),
            Map.entry("reduceOnlyCanceled", OrderState.CANCELED),
            Map.entry("siblingFilledCanceled", OrderState.CANCELED),
            Map.entry("delistedCanceled", OrderState.CANCELED),
            Map.entry("liquidatedCanceled", OrderState.CANCELED),
            Map.entry("scheduledCancel", OrderState.CANCELED),

            // 거부
            Map.entry("rejected", OrderState.FAILED),
            Map.entry("tickRejected", OrderState.FAILED),
            Map.entry("minTradeNtlRejected", OrderState.FAILED),
            Map.entry("perpMarginRejected", OrderState.FAILED),
            Map.entry("reduceOnlyRejected", OrderState.FAILED),
            Map.entry("badAloPxRejected", OrderState.FAILED),
            Map.entry("iocCancelRejected", OrderState.FAILED),
            Map.entry("badTriggerPxRejected", OrderState.FAILED),
            Map.entry("marketOrderNoLiquidityRejected", OrderState.FAILED),
            Map.entry("positionIncreaseAtOpenInterestCapRejected", OrderState.FAILED),
            Map.entry("positionFlipAtOpenInterestCapRejected", OrderState.FAILED),
            Map.entry("tooAggressiveAtOpenInterestCapRejected", OrderState.FAILED),
            Map.entry("openInterestIncreaseRejected", OrderState.FAILED),
            Map.entry("oracleRejected", OrderState.FAILED)
    );

    public static final Map<TimeInForce, String> TIME_IN_FORCE_API_VALUE = Map.of(
            TimeInForce.GTC, "Gtc",
            TimeInForce.IOC, "Ioc",
            TimeInForce.POST_ONLY, "Alo"
    );
    public static final Set<TimeInForce> SUPPORTED_TIME_IN_FORCE = Set.of(
            TimeInForce.GTC,
            TimeInForce.IOC,
            TimeInForce.POST_ONLY
    );
    public static final int MAX_ORDER_ID_LENGTH = 34;
    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);

    // endpoint-specific soft limit
    private static final int MAX_REQUEST = 1200;
    private static final int NOT_USED = 0;

    // Pool 한도
    private static final int REST_WEIGHT_LIMIT_PER_MIN = 1200;
    private static final int EXPLORER_WEIGHT_LIMIT_PER_MIN = 1200;

    public static final String REST_WEIGHT_POOL = "REST_WEIGHT";

    public static final List<RateLimit> RATE_LIMITS = List.of(
            RateLimit.pool(REST_WEIGHT_POOL, REST_WEIGHT_LIMIT_PER_MIN, Duration.ofMinutes(1)),
//            RateLimit.pool(EXPLORER_WEIGHT_POOL, EXPLORER_WEIGHT_LIMIT_PER_MIN, Duration.ofMinutes(1)),

            // Info endpoints
            RateLimit.endpoint(INFO_PATH_URL, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20)))

//            RateLimit.endpoint(META_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            RateLimit.endpoint(ALL_MIDS_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            RateLimit.endpoint(USER_STATE_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            RateLimit.endpoint(OPEN_ORDERS_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//
//            // 페이지네이션 info - base weight 20, 아이템별 추가는 동적으로 계산 필요
//            // (정적 상수로는 base만 표현, 호출 시점에 실제 weight 조정)
//            RateLimit.endpoint(RECENT_TRADES_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            RateLimit.endpoint(USER_FILLS_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            RateLimit.endpoint(FUNDING_HISTORY_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            RateLimit.endpoint(CANDLE_SNAPSHOT_INFO, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 20))),
//
//            // Exchange actions - 기본 weight 1 (batch size에 따라 동적 조정)
//            RateLimit.endpoint(ORDER_ACTION, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 1))),
//
//            RateLimit.endpoint(CANCEL_ACTION, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 1))),
//
//            RateLimit.endpoint(MODIFY_ACTION, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 1))),
//
//            RateLimit.endpoint(UPDATE_LEVERAGE_ACTION, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 1))),
//
//            RateLimit.endpoint(UPDATE_ISOLATED_MARGIN_ACTION, Duration.ofMinutes(1), MAX_REQUEST, NOT_USED,
//                    List.of(new RateLimit.LinkedLimitWeightPair(REST_WEIGHT_POOL, 1)))
    );

}
