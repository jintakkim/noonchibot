package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.trade.TradeType;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class OrderClientImpl implements OrderClient {
    private final RestAssistant restAssistant;
    private final HLTradingRuleRegistry tradingRuleRegistry;

    OrderClientImpl(RestAssistant restAssistant, HLTradingRuleRegistry tradingRuleRegistry) {
        this.restAssistant = restAssistant;
        this.tradingRuleRegistry = tradingRuleRegistry;
    }

    @Override
    public OrderPlaceResult placeOrder(InFlightOrder order) {
        HLTradingRuleRegistry.AssetMeta meta = tradingRuleRegistry.getAssetMeta(order.getTradingPair());
        Map<String, Object> orderPayload = new LinkedHashMap<>();
        orderPayload.put("a", meta.assetId());
        orderPayload.put("b", order.getTradeType() == TradeType.BUY);
        orderPayload.put("p", order.getPrice().toPlainString());
        orderPayload.put("s", order.getAmount().toPlainString());
        orderPayload.put("r", false);
        orderPayload.put("t", Map.of("limit", Map.of("tif", timeInForceApiValue(order))));
        orderPayload.put("c", order.getClientOrderId());

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.EXCHANGE_PATH_URL)
                .body(Map.of(
                        "action", Map.of(
                                "type", "order",
                                "orders", List.of(orderPayload),
                                "grouping", "na"
                        )
                ))
                .authRequired(true)
                .build());

        JsonNode status = response.path("response").path("data").path("statuses").get(0);
        if (status.has("error")) {
            throw new IllegalStateException(status.get("error").asString());
        }
        if (status.has("resting")) {
            return new OrderPlaceResult(String.valueOf(status.get("resting").get("oid").asLong()), OrderState.OPEN, Instant.now());
        }
        if (status.has("filled")) {
            return new OrderPlaceResult(String.valueOf(status.get("filled").get("oid").asLong()), OrderState.FILLED, Instant.now());
        }
        throw new IllegalStateException("Unexpected hyperliquid order response: " + response);
    }

    @Override
    public OrderCancelResult cancelOrder(String tradingPair, String clientOrderId) {
        HLTradingRuleRegistry.AssetMeta meta = tradingRuleRegistry.getAssetMeta(tradingPair);
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.EXCHANGE_PATH_URL)
                .body(Map.of(
                        "action", Map.of(
                                "type", "cancelByCloid",
                                "cancels", List.of(Map.of(
                                        "asset", meta.assetId(),
                                        "cloid", clientOrderId
                                ))
                        )
                ))
                .authRequired(true)
                .build());

        JsonNode status = response.path("response").path("data").path("statuses").get(0);
        if ("success".equals(status.asString())) {
            return new OrderCancelResult(true, Instant.now());
        }
        if (status.has("error")) {
            throw new IllegalStateException(status.get("error").asString());
        }
        throw new IllegalStateException("Unexpected hyperliquid cancel response: " + response);
    }

    @Override
    public Set<TimeInForce> getSupportedTimeInForce() {
        return DerivativeApiSpec.SUPPORTED_TIME_IN_FORCE;
    }

    private String timeInForceApiValue(InFlightOrder order) {
        if (order.isPostOnly()) return DerivativeApiSpec.TIME_IN_FORCE_API_VALUE.get(TimeInForce.POST_ONLY);
        return DerivativeApiSpec.TIME_IN_FORCE_API_VALUE.get(order.getTimeInForce());
    }
}
