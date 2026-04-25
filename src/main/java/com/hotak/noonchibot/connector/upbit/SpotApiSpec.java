package com.hotak.noonchibot.connector.upbit;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.TimeInForce;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpotApiSpec {
    // 업비트는 identifier 제약이 공식 문서에 명시되지 않음
    public static final String ORDER_ID_PREFIX = "";
    public static final int MAX_ORDER_ID_LENGTH = 32;

    public static final String PLATFORM_NAME = "upbit";

    public static final String REST_BASE_URL = "https://api.upbit.com";
    public static final String WSS_URL = "wss://api.upbit.com/websocket/v1";
    public static final String WSS_API_URL = "wss://api.upbit.com/websocket/v1/private";
    public static final String TICKER_PRICE_CHANGE_PATH_URL = "/v1/ticker";
    public static final String SNAPSHOT_PATH_URL = "/v1/orderbook";
    public static final String EXCHANGE_INFO_PATH_URL = "/v1/market/all";
    public static final String CREATE_ORDER_PATH_URL = "/v1/orders";
    public static final String ORDER_PATH_URL = "/v1/order";
    public static final String ACCOUNTS_PATH_URL = "/v1/accounts";
    public static final String ORDERS_BY_UUIDS_PATH_URL = "/v1/orders/uuids";

    public static final Map<TimeInForce, String> TIME_IN_FORCE_API_VALUE = Map.of(
            TimeInForce.FOK, "fok",
            TimeInForce.IOC, "ioc",
            TimeInForce.POST_ONLY, "post_only"
            // GTC는 기본값이므로 파라미터가 생략된다
    );

    // 업비트는 wait, watch, done, cancel 네 가지의 ORDER STATE가 존재한다.
    public static OrderState parseOrderState(JsonNode order) {
        String state = order.get("state").asString();
        BigDecimal executedVolume = order.path("executed_volume").asDecimal(BigDecimal.ZERO);
        BigDecimal remainingVolume = order.path("remaining_volume").asDecimal(BigDecimal.ZERO);

        return switch (state) {
            case "wait", "watch" -> executedVolume.signum() > 0
                    ? OrderState.PARTIALLY_FILLED
                    : OrderState.OPEN;
            // 딱 맞아떨어지는 체결이 발생할 시 DONE 처리
            case "done" -> OrderState.FILLED;
            // 시장가로 주문이 체결되어 주문 잔량이 남을 시 (미미한 양이다) cancel 처리가 된다
            case "cancel" -> (executedVolume.signum() > 0 && remainingVolume.signum() == 0)
                    ? OrderState.FILLED
                    : OrderState.CANCELED;
            default -> throw new IllegalArgumentException("Unknown upbit state: " + state);
        };
    }

    private static final int MAX_REQUEST = 99999;
    private static final int NOT_USED = 1;

    public static final List<RateLimit> RATE_LIMITS = List.of(
            RateLimit.pool("QUOTATION", 10, Duration.ofSeconds(1)),
            RateLimit.pool("EXCHANGE_DEFAULT_SEC", 30, Duration.ofSeconds(1)),
            RateLimit.pool("EXCHANGE_DEFAULT_MIN", 900, Duration.ofMinutes(1)),
            RateLimit.pool("ORDER_SEC", 8, Duration.ofSeconds(1)),
            RateLimit.pool("ORDER_MIN", 200, Duration.ofMinutes(1)),

            RateLimit.endpoint(TICKER_PRICE_CHANGE_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(new RateLimit.LinkedLimitWeightPair("QUOTATION", 1))),
            RateLimit.endpoint(SNAPSHOT_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(new RateLimit.LinkedLimitWeightPair("QUOTATION", 1))),
            RateLimit.endpoint(EXCHANGE_INFO_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(new RateLimit.LinkedLimitWeightPair("QUOTATION", 1))),

            RateLimit.endpoint(ACCOUNTS_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("EXCHANGE_DEFAULT_SEC", 1),
                            new RateLimit.LinkedLimitWeightPair("EXCHANGE_DEFAULT_MIN", 1)
                    )),
            RateLimit.endpoint(ORDERS_BY_UUIDS_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("EXCHANGE_DEFAULT_SEC", 1),
                            new RateLimit.LinkedLimitWeightPair("EXCHANGE_DEFAULT_MIN", 1)
                    )),
            RateLimit.endpoint(CREATE_ORDER_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("ORDER_SEC", 1),
                            new RateLimit.LinkedLimitWeightPair("ORDER_MIN", 1)
                    )),
            RateLimit.endpoint(ORDER_PATH_URL, Duration.ofSeconds(1), MAX_REQUEST, NOT_USED,
                    List.of(
                            new RateLimit.LinkedLimitWeightPair("ORDER_SEC", 1),
                            new RateLimit.LinkedLimitWeightPair("ORDER_MIN", 1)
                    ))
    );

    public static final String ORDER_NOT_FOUND_MESSAGE = "order_not_found";

    public static final Duration TRADING_RULE_UPDATE_INTERVAL = Duration.ofHours(1);
}
