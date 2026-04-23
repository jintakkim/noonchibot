package com.hotak.noonchibot.connector.bybit;

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
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class SpotOrderExecutor extends AbstractExchangeOrderExecutor {
    private final TimeSynchronizer timeSynchronizer;
    private final RestAssistant restAssistant;

    public SpotOrderExecutor(
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
                SpotApiSpec.PLATFORM_NAME,
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                true,
                SpotApiSpec.ORDER_ID_PREFIX,
                SpotApiSpec.MAX_ORDER_ID_LENGTH,
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
                && message.contains(String.valueOf(SpotApiSpec.TIMESTAMP_ERROR_CODE));
    }

    @Override
    protected boolean isOrderNotFoundDuringCancellationException(Throwable e) {
        String message = e.getMessage();
        if (message == null) return false;

        return message.contains(String.valueOf(SpotApiSpec.ORDER_NOT_EXIST_ERROR_CODE))
                || message.contains(String.valueOf(SpotApiSpec.UNKNOWN_ORDER_ERROR_CODE));
    }

    @Override
    protected OrderPlacedDto placeOrder(InFlightOrder inFlightOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(inFlightOrder.getTradingPair());
        String tradeTypeApiValue = inFlightOrder.getTradeType() == TradeType.BUY ? "Buy" : "Sell";
        String orderTypeApiValue = orderTypeToApiValue(inFlightOrder.getOrderType());

        Map<String, Object> body = new HashMap<>();
        body.put("category", "spot");
        body.put("symbol", symbol);
        body.put("side", tradeTypeApiValue);
        body.put("orderType", orderTypeApiValue);
        body.put("qty", inFlightOrder.getAmount().toPlainString());
        body.put("orderLinkId", inFlightOrder.getClientOrderId());

        if (inFlightOrder.getOrderType() == OrderType.LIMIT) {
            body.put("price", inFlightOrder.getPrice().toPlainString());
            if (inFlightOrder.isPostOnly()) {
                body.put("timeInForce", "PostOnly");
            } else {
                body.put("timeInForce", SpotApiSpec.TIME_IN_FORCE_API_VALUE.get(inFlightOrder.getTimeInForce()));
            }
        }

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(SpotApiSpec.ORDER_CREATE_PATH_URL)
                .authRequired(true)
                .body(body)
                .build();

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);
        int retCode = response.path("retCode").asInt();

        if (retCode == 0) {
            String exchangeOrderId = response.path("result").path("orderId").asString();
            return new OrderPlacedDto(exchangeOrderId, Instant.ofEpochMilli(timeSynchronizer.serverTime()));
        } else if (retCode == 10016) {
            return new OrderPlacedDto("UNKNOWN", Instant.ofEpochMilli(timeSynchronizer.serverTime()));
        } else {
            String retMsg = response.path("retMsg").asString();
            throw new RuntimeException("Bybit API Order Failed: [" + retCode + "] " + retMsg);
        }
    }

    @Override
    protected boolean placeCancel(String orderId, InFlightOrder trackedOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(trackedOrder.getTradingPair());

        Map<String, Object> body = new HashMap<>();
        body.put("category", "spot");
        body.put("symbol", symbol);
        body.put("orderLinkId", orderId);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(SpotApiSpec.ORDER_CANCEL_PATH_URL)
                .body(body)
                .authRequired(true)
                .build();

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);
        int retCode = response.path("retCode").asInt();
        return retCode == 0;
    }

    private static String orderTypeToApiValue(OrderType orderType) {
        return switch (orderType) {
            case LIMIT -> "Limit";
            case MARKET -> "Market";
            default -> throw new IllegalArgumentException("Unsupported OrderType: " + orderType);
        };
    }
}