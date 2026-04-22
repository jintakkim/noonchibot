package com.hotak.noonchibot.connector.derivative.bybit;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.order.execute.AbstractExchangeOrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BybitDerivativeOrderExecutor extends AbstractExchangeOrderExecutor {
    private final TimeSynchronizer timeSynchronizer;
    private final RestAssistant restAssistant;

    public BybitDerivativeOrderExecutor(
            OrderIdGenerator orderIdGenerator,
            OrderTracker orderTracker,
            TradingRuleRegistry tradingRuleRegistry,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookTracker orderBookTracker,
            TimeSynchronizer timeSynchronizer,
            ExchangeEventPublisher exchangeEventPublisher,
            RestAssistant restAssistant,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor
    ) {
        super(
                BybitDerivativeApiSpec.PLATFORM_NAME,
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                true,
                BybitDerivativeApiSpec.ORDER_ID_PREFIX,
                BybitDerivativeApiSpec.MAX_ORDER_ID_LENGTH,
                tradingPairSymbolRegistry,
                orderBookTracker,
                exchangeEventPublisher,
                mainExecutor,
                ioExecutor
        );
        this.timeSynchronizer = timeSynchronizer;
        this.restAssistant = restAssistant;
    }

    @Override
    public Set<OrderType> getSupportedOrderType(String tradingPair) {
        TradingRule rule = tradingRuleRegistry.getTradingRule(tradingPair);
        if (rule == null) throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
        return rule.supportedOrderTypes();
    }

    @Override
    public Set<TimeInForce> getSupportedTimeInForce() {
        return Set.of(TimeInForce.FOK, TimeInForce.GTC, TimeInForce.IOC);
    }

    @Override
    protected boolean isRequestExceptionRelatedToTimeSynchronizer(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BybitDerivativeApiSpec.TIMESTAMP_ERROR_CODE));
    }

    @Override
    protected boolean isOrderNotFoundDuringCancellationException(Throwable e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BybitDerivativeApiSpec.ORDER_NOT_EXIST_ERROR_CODE));
    }

    @Override
    protected OrderPlacedDto placeOrder(InFlightOrder inFlightOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(inFlightOrder.getTradingPair());
        String tradeTypeApiValue = inFlightOrder.getTradeType() == TradeType.BUY ? "Buy" : "Sell";
        String orderTypeApiValue = orderTypeToApiValue(inFlightOrder.getOrderType());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("category", "linear");
        requestBody.put("symbol", symbol);
        requestBody.put("side", tradeTypeApiValue);
        requestBody.put("orderType", orderTypeApiValue);
        requestBody.put("qty", inFlightOrder.getAmount().toPlainString());
        requestBody.put("orderLinkId", inFlightOrder.getClientOrderId());

        if (inFlightOrder.getOrderType() == OrderType.LIMIT) {
            requestBody.put("price", inFlightOrder.getPrice().toPlainString());
            if(inFlightOrder.isPostOnly() && inFlightOrder.getTimeInForce() == TimeInForce.GTC) {
                requestBody.put("timeInForce", "PostOnly");
            } else {
                requestBody.put("timeInForce", BybitDerivativeApiSpec.TIME_IN_FORCE_API_VALUE.get(inFlightOrder.getTimeInForce()));
            }
        }
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(BybitDerivativeApiSpec.ORDER_CREATE_PATH_URL)
                .authRequired(true)
                .body(requestBody)
                .build();
        try {
            JsonNode orderResult = restAssistant.executeRequestAndGetJsonBody(request);

            int retCode = orderResult.path("retCode").asInt(-1);
            if (retCode != 0) {
                throw new ExchangeApiException(
                        HttpStatusCode.valueOf(200),
                        orderResult.path("retMsg").asString()
                );
            }

            JsonNode result = orderResult.get("result");
            String exchangeOrderId = result.get("orderId").asString();
            Instant transactTime = Instant.ofEpochMilli(orderResult.get("time").asLong());
            return new OrderPlacedDto(exchangeOrderId, transactTime);

        } catch (ExchangeApiException e) {
            if(e.httpStatusCode.is5xxServerError()) {
                return new OrderPlacedDto("UNKNOWN", Instant.ofEpochMilli(timeSynchronizer.serverTime()));
            }
            throw e;
        }
    }

    @Override
    protected boolean placeCancel(String orderId, InFlightOrder order) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(order.getTradingPair());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("category", "linear");
        requestBody.put("symbol", symbol);
        requestBody.put("orderLinkId", orderId);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(BybitDerivativeApiSpec.ORDER_CANCEL_PATH_URL)
                .body(requestBody)
                .authRequired(true)
                .build();

        JsonNode result = restAssistant.executeRequestAndGetJsonBody(request);
        return result.path("retCode").asInt(-1) == 0;
    }

    private static String orderTypeToApiValue(OrderType orderType) {
        return switch (orderType) {
            case LIMIT -> "Limit";
            case MARKET -> "Market";
            case AMM_SWAP -> throw new IllegalArgumentException(
                    "AMM_SWAP is not supported on Bybit"
            );
        };
    }
}
