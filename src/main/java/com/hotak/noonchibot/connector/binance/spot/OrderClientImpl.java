package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
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
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public OrderClientImpl(
            TimeSynchronizer timeSynchronizer,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
    ) {
        this.timeSynchronizer = timeSynchronizer;
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    public Set<TimeInForce> getSupportedTimeInForce() {
        return ApiSpec.SUPPORTED_TIME_IN_FORCE;
    }

    @Override
    public OrderPlaceResult placeOrder(InFlightOrder inFlightOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(inFlightOrder.getTradingPair());
        String tradeTypeApiValue = inFlightOrder.getTradeType() == TradeType.BUY ? "BUY" : "SELL";
        String orderTypeApiValue = orderTypeToApiValue(inFlightOrder.getOrderType(), inFlightOrder.isPostOnly());

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("side", tradeTypeApiValue);
        apiParams.put("quantity", inFlightOrder.getAmount().toPlainString());
        apiParams.put("type", orderTypeApiValue);
        apiParams.put("newClientOrderId", inFlightOrder.getClientOrderId());

        if (inFlightOrder.getOrderType() == OrderType.LIMIT) {
            apiParams.put("price", inFlightOrder.getPrice().toPlainString());
            if (!inFlightOrder.isPostOnly()) {
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
            JsonNode orderResult = restAssistant.executeRequestAndGetJsonBody(request);
            String exchangeOrderId = orderResult.get("orderId").asString();
            Instant transactTime = Instant.ofEpochMilli(orderResult.get("transactTime").asLong());
            String orderStatus = orderResult.path("status").asString();
            OrderState orderState = ApiSpec.ORDER_STATE.getOrDefault(orderStatus, OrderState.OPEN);

            return new OrderPlaceResult(exchangeOrderId, orderState, transactTime);
        } catch (ExchangeApiException e) {
            if (e.httpStatusCode == HttpStatusCode.valueOf(503)
                    && e.getMessage().contains("Unknown error, please check your request or try again later.")) {
                return new OrderPlaceResult(null, OrderState.PENDING_CREATE, Instant.ofEpochMilli(timeSynchronizer.serverTime()));
            }
            throw e;
        }
    }

    @Override
    public OrderCancelResult cancelOrder(String tradingPair, String clientOrderId) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.DELETE)
                .pathUrl(ApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", clientOrderId))
                .authRequired(true)
                .build();

        restAssistant.executeRequestAndGetResponse(request);
        return new OrderCancelResult(true, Instant.ofEpochMilli(timeSynchronizer.serverTime()));
    }

    private static String orderTypeToApiValue(OrderType orderType, boolean postOnly) {
        if (orderType == OrderType.LIMIT && postOnly) {
            return "LIMIT_MAKER";
        }
        return orderType.name().toUpperCase();
    }
}
