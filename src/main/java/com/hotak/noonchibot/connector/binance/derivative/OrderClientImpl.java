package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.ExchangeRestApiException;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.trade.TradeType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class OrderClientImpl implements OrderClient {
    private final TimeSynchronizer timeSynchronizer;
    private final RestAssistant orderEntryRestAssistant;
    private final RestAssistant orderCancelRestAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public OrderClientImpl(
            TimeSynchronizer timeSynchronizer,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        this(timeSynchronizer, restAssistant, restAssistant, tradingPairSymbolRegistry);
    }

    public OrderClientImpl(
            TimeSynchronizer timeSynchronizer,
            RestAssistant orderEntryRestAssistant,
            RestAssistant orderCancelRestAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        this.timeSynchronizer = timeSynchronizer;
        this.orderEntryRestAssistant = orderEntryRestAssistant;
        this.orderCancelRestAssistant = orderCancelRestAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    public Set<TimeInForce> getSupportedTimeInForce() {
        return Set.of(TimeInForce.FOK, TimeInForce.GTC, TimeInForce.IOC);
    }

    /**
     * -- 바이낸스 특수 케이스 고려 --
     * 바이낸스의 경우에는 503, unknown error라면 오더 채결 여부는 미정이다.
     * 추후 api를 통해 오더 채결 여부를 확정해야한다.
     * 따라서 해당 조건일때 리턴되는 ExchangeOrderId는 "UNKNOWN" 이다.
     */
    @Override
    public OrderPlaceSuccess placeOrder(InFlightOrder inFlightOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(inFlightOrder.getTradingPair());
        String tradeTypeApiValue = inFlightOrder.getTradeType() == TradeType.BUY ? "BUY" : "SELL";
        String orderTypeApiValue = orderTypeToApiValue(inFlightOrder.getOrderType());

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("side", tradeTypeApiValue);
        apiParams.put("quantity", inFlightOrder.getAmount().toPlainString());
        apiParams.put("type", orderTypeApiValue);
        apiParams.put("newClientOrderId", inFlightOrder.getClientOrderId());

        if (inFlightOrder.getOrderType() == OrderType.LIMIT) {
            apiParams.put("price", inFlightOrder.getPrice().toPlainString());
            if(inFlightOrder.isPostOnly() && inFlightOrder.getTimeInForce() == TimeInForce.GTC) {
                apiParams.put("timeInForce", "GTX");
            } else {
                apiParams.put("timeInForce", ApiSpec.TIME_IN_FORCE_API_VALUE.get(inFlightOrder.getTimeInForce()));
            }
        }
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(ApiSpec.ORDER_PATH_URL)
                .authRequired(true)
                .params(apiParams)
                .build();
        try {
            JsonNode orderResult = orderEntryRestAssistant.executeRequestAndGetJsonBody(request);
            String exchangeOrderId = orderResult.get("orderId").asString();
            Instant timestamp = parseOrderTimestamp(orderResult);
            String orderStatus = orderResult.path("status").asString();
            OrderState orderState = ApiSpec.ORDER_STATE.getOrDefault(orderStatus, OrderState.OPEN);

            return new OrderPlaceSuccess(exchangeOrderId, orderState, timestamp);
        } catch (ExchangeRestApiException e) {
            if(e.statusCode().isSameCodeAs(HttpStatusCode.valueOf(503)) && e.responseBody().contains("Unknown error, please check your request or try again later.")) {
                return new OrderPlaceSuccess(null, OrderState.PENDING_CREATE ,Instant.ofEpochMilli(timeSynchronizer.serverTime()));
            }
            throw e;
        }
    }

    @Override
    public OrderCancelSuccess cancelOrder(String tradingPair, String clientOrderId) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("origClientOrderId", clientOrderId);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.DELETE)
                .pathUrl(ApiSpec.ORDER_PATH_URL)
                .params(apiParams)
                .authRequired(true)
                .build();

        orderCancelRestAssistant.executeRequestAndGetResponse(request);
        return new OrderCancelSuccess(true, Instant.ofEpochMilli(timeSynchronizer.serverTime()));
    }

    private static String orderTypeToApiValue(OrderType orderType) {
        return orderType.name().toUpperCase();
    }

    private static Instant parseOrderTimestamp(JsonNode orderResult) {
        JsonNode timestamp = orderResult.get("updateTime");
        if (timestamp == null) {
            timestamp = orderResult.get("transactTime");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Order response does not contain updateTime or transactTime: " + orderResult);
        }
        return Instant.ofEpochMilli(timestamp.asLong());
    }

}
