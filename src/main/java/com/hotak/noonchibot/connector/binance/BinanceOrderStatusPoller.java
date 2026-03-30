package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.AbstractExchangeDataPoller;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.OrderStreamStatus;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Slf4j
public class BinanceOrderStatusPoller extends AbstractExchangeDataPoller {
    private final RestAssistant restAssistant;
    private final OrderTracker orderTracker;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public BinanceOrderStatusPoller(
            RestAssistant restAssistant,
            OrderTracker orderTracker,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderStreamStatus orderStreamStatus,
            AsyncTaskExecutor taskExecutor
    ) {
        super(orderStreamStatus, taskExecutor);
        this.restAssistant = restAssistant;
        this.orderTracker = orderTracker;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    protected void pollData() {
        executeParallel(
                orderTracker.getActiveOrders().values(),
                this::updateOrderStatus
        );
    }

    private void updateOrderStatus(InFlightOrder inFlightOrder) {
        try {
            OrderUpdateEvent orderUpdateEvent = fetchOrderStatus(inFlightOrder);
            orderTracker.processOrderUpdate(orderUpdateEvent);
        } catch (Exception e) {
            log.warn("inFlightOrder 상태 업데이트 중 예외 발생(not found order로 전환)", e);
            orderTracker.processOrderNotFound(inFlightOrder.getClientOrderId());
        }
    }

    private OrderUpdateEvent fetchOrderStatus(InFlightOrder inFlightOrder) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(inFlightOrder.getTradingPair());
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", inFlightOrder.getClientOrderId()))
                .authRequired(true)
                .build();
        JsonNode updatedOrder = restAssistant.executeRequestAndGetJsonBody(request);
        InFlightOrder.State newState = BinanceApiSpec.ORDER_STATE.get(updatedOrder.get("status").asString());
        return new OrderUpdateEvent(
                inFlightOrder.getTradingPair(),
                Instant.ofEpochMilli(updatedOrder.get("updateTime").asLong()),
                newState,
                inFlightOrder.getClientOrderId(),
                updatedOrder.get("orderId").asString()
        );
    }


    private void executeParallel(Collection<InFlightOrder> inFlightOrders, Consumer<InFlightOrder> task) {
        List<Future<?>> futures = new ArrayList<>();
        for (InFlightOrder inFlightOrder : inFlightOrders) {
            futures.add(taskExecutor.submit(() -> task.accept(inFlightOrder)));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // task 내부에서 처리
            }
        }
    }
}
