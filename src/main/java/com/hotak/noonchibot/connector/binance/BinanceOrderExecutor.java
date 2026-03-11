package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.datatype.*;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.executor.AbstractExchangeOrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
public class BinanceOrderExecutor extends AbstractExchangeOrderExecutor {
    private static final String EXCHANGE_NAME = "binance";

    private final TimeSynchronizer timeSynchronizer;
    private final RestAssistant restAssistant;

    public BinanceOrderExecutor(
            OrderIdGenerator orderIdGenerator,
            OrderTracker orderTracker,
            TradingRuleRegistry tradingRuleRegistry,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookDataSource orderBookDataSource,
            TimeSynchronizer timeSynchronizer,
            ExchangeEventPublisher exchangeEventPublisher,
            RestAssistant restAssistant
            ) {
        super(
                EXCHANGE_NAME,
                orderIdGenerator,
                orderTracker,
                tradingRuleRegistry,
                true,
                BinanceApiSpec.ORDER_ID_PREFIX,
                BinanceApiSpec.MAX_ORDER_ID_LENGTH,
                tradingPairSymbolRegistry,
                orderBookDataSource,
                exchangeEventPublisher
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
    protected boolean isRequestExceptionRelatedToTimeSynchronizer(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.TIMESTAMP_ERROR_CODE))
                && message.contains(BinanceApiSpec.TIMESTAMP_ERROR_MESSAGE);
    }

    @Override
    protected boolean isOrderNotFoundDuringStatusUpdateException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.ORDER_NOT_EXIST_ERROR_CODE))
                && message.contains(BinanceApiSpec.ORDER_NOT_EXIST_MESSAGE);
    }

    @Override
    protected boolean isOrderNotFoundDuringCancellationException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.UNKNOWN_ORDER_ERROR_CODE))
                && message.contains(BinanceApiSpec.UNKNOWN_ORDER_MESSAGE);
    }

    /**
     * -- 바이낸스 특수 케이스 고려 --
     * 바이낸스의 경우에는 503, unknown error라면 오더 채결 여부는 미정이다.
     * 추후 api를 통해 오더 채결 여부를 확정해야한다.
     * 따라서 해당 조건일때 리턴되는 ExchangeOrderId는 "UNKNOWN" 이다.
     */
    @Override
    protected OrderPlacedDto placeOrder(String orderId, String tradingPair, BigDecimal amount, TradeType tradeType, OrderType orderType, BigDecimal price, Object... args) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        String tradeTypeApiValue = tradeType == TradeType.BUY ? "BUY" : "SELL";
        String orderTypeApiValue = orderTypeToApiValue(orderType);

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("side", tradeTypeApiValue);
        apiParams.put("quantity", amount.toPlainString());
        apiParams.put("type", orderTypeApiValue);
        apiParams.put("newClientOrderId", orderId);

        if (orderType == OrderType.LIMIT || orderType == OrderType.LIMIT_MAKER) {
            apiParams.put("price", price.toPlainString());
        }
        if (orderType == OrderType.LIMIT) {
            apiParams.put("timeInForce", BinanceApiSpec.TIME_IN_FORCE_GTC);
        }
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
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
    protected boolean placeCancel(String orderId, InFlightOrder trackedOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(trackedOrder.getTradingPair());

        Map<String, Object> apiParams = new HashMap<>();
        apiParams.put("symbol", symbol);
        apiParams.put("origClientOrderId", orderId);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.DELETE)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
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
