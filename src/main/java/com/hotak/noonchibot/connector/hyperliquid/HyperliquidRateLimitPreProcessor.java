package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.RestPreProcessor;
import com.hotak.noonchibot.connector.web.RestRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class HyperliquidRateLimitPreProcessor implements RestPreProcessor {
    private static final Set<String> LIGHT_INFO_TYPES = Set.of(
            "l2Book",
            "allMids",
            "clearinghouseState",
            "orderStatus",
            "spotClearinghouseState",
            "exchangeStatus"
    );
    private static final Set<String> RESPONSE_WEIGHTED_INFO_TYPES = Set.of(
            "recentTrades",
            "historicalOrders",
            "userFills",
            "userFillsByTime",
            "fundingHistory",
            "userFunding",
            "nonUserFundingUpdates",
            "twapHistory",
            "userTwapSliceFills",
            "userTwapSliceFillsByTime",
            "delegatorHistory",
            "delegatorRewards",
            "validatorStats"
    );

    private static final int LIGHT_INFO_WEIGHT = 2;
    private static final int DEFAULT_INFO_WEIGHT = 20;
    private static final int USER_ROLE_WEIGHT = 60;
    private static final int MAX_RESPONSE_ITEMS = 2_000;
    private static final int RESPONSE_ITEMS_PER_WEIGHT = 20;

    @Override
    public RestRequest process(RestRequest request) {
        int weight = switch (request.pathUrl()) {
            case DerivativeApiSpec.INFO_PATH_URL -> infoWeight(request.body());
            case DerivativeApiSpec.EXCHANGE_PATH_URL -> exchangeWeight(request.body());
            default -> 1;
        };

        return request.toBuilder()
                .throttlerLimitId(request.pathUrl())
                .weightOverrides(Map.of(DerivativeApiSpec.REST_WEIGHT_POOL, weight))
                .build();
    }

    private int infoWeight(Object body) {
        String type = stringValue(mapValue(body).get("type"));
        if (LIGHT_INFO_TYPES.contains(type)) return LIGHT_INFO_WEIGHT;
        if ("userRole".equals(type)) return USER_ROLE_WEIGHT;
        if (RESPONSE_WEIGHTED_INFO_TYPES.contains(type)) {
            return DEFAULT_INFO_WEIGHT + MAX_RESPONSE_ITEMS / RESPONSE_ITEMS_PER_WEIGHT;
        }
        return DEFAULT_INFO_WEIGHT;
    }

    private int exchangeWeight(Object body) {
        Map<?, ?> action = mapValue(mapValue(body).get("action"));
        int batchLength = List.of("orders", "cancels", "modifies").stream()
                .map(action::get)
                .filter(List.class::isInstance)
                .mapToInt(value -> ((List<?>) value).size())
                .findFirst()
                .orElse(0);
        return 1 + batchLength / 40;
    }

    private Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : "";
    }
}
