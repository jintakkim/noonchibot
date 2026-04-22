package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.order.execute.AbstractExchangeOrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * todo: post only 적용 옵션 추가
 */
public class BinanceDerivativeOrderExecutor extends AbstractExchangeOrderExecutor {
    private final TimeSynchronizer timeSynchronizer;
    private final RestAssistant restAssistant;

    public BinanceDerivativeOrderExecutor(
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
                BinanceDerivativeApiSpec.PLATFORM_NAME,
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                true,
                BinanceDerivativeApiSpec.ORDER_ID_PREFIX,
                BinanceDerivativeApiSpec.MAX_ORDER_ID_LENGTH,
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
        if(rule == null) throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
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
                && message.contains(String.valueOf(BinanceDerivativeApiSpec.TIMESTAMP_ERROR_CODE));
    }

    @Override
    protected boolean isOrderNotFoundDuringCancellationException(Throwable e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceDerivativeApiSpec.UNKNOWN_ORDER_DURING_CANCELLATION_ERROR_CODE));
    };

    /**
     * -- 바이낸스 특수 케이스 고려 --
     * 바이낸스의 경우에는 503, unknown error라면 오더 채결 여부는 미정이다.
     * 추후 api를 통해 오더 채결 여부를 확정해야한다.
     * 따라서 해당 조건일때 리턴되는 ExchangeOrderId는 "UNKNOWN" 이다.
     */
    @Override
    protected OrderPlacedDto placeOrder(InFlightOrder inFlightOrder) {
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
                apiParams.put("timeInForce", BinanceDerivativeApiSpec.TIME_IN_FORCE_API_VALUE.get(inFlightOrder.getTimeInForce()));
            }
        }
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(BinanceDerivativeApiSpec.ORDER_PATH_URL)
                .authRequired(true)
                .params(apiParams)
                .build();
        try {
            JsonNode orderResult = restAssistant.executeRequestAndGetJsonBody(request);
            String exchangeOrderId = orderResult.get("orderId").asString();
            Instant transactTime = Instant.ofEpochMilli(orderResult.get("transactTime").asLong());
            return new OrderPlacedDto(exchangeOrderId, transactTime);
        } catch (ExchangeApiException e) {
            if(e.httpStatusCode == HttpStatusCode.valueOf(503) && e.getMessage().contains("Unknown error, please check your request or try again later.")) {
                return new OrderPlacedDto("UNKNOWN", Instant.ofEpochMilli(timeSynchronizer.serverTime()));
            }
            throw e;
        }
    }

    @Override
    protected boolean placeCancel(String orderId, InFlightOrder order) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(order.getTradingPair());

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("origClientOrderId", orderId);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.DELETE)
                .pathUrl(BinanceDerivativeApiSpec.ORDER_PATH_URL)
                .params(apiParams)
                .authRequired(true)
                .build();

        JsonNode result = restAssistant.executeRequestAndGetJsonBody(request);
        return "CANCELED".equals(result.path("status").asString());
    }

    private static String orderTypeToApiValue(OrderType orderType) {
        return orderType.name().toUpperCase();
    }
}
