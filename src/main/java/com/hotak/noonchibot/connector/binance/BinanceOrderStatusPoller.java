package com.hotak.noonchibot.connector.binance;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.PollScheduler;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderLostEvent;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class BinanceOrderStatusPoller implements SmartLifecycle {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final OrderTracker orderTracker;
    private final MainExecutor mainExecutor;
    private final IoExecutor ioExecutor;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final PollScheduler pollScheduler;
    private volatile boolean running = false;

    public BinanceOrderStatusPoller(
            RestAssistant restAssistant,
            ExchangeEventPublisher eventPublisher,
            OrderTracker orderTracker,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            WebsocketStatus websocketStatus,
            TaskScheduler scheduler
    ) {
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.orderTracker = orderTracker;
        this.mainExecutor = mainExecutor;
        this.ioExecutor = ioExecutor;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.pollScheduler = new PollScheduler(websocketStatus, scheduler);
    }

    private record OrderPollRequest(String tradingPair, String clientOrderId) {}

    @VisibleForTesting
    void pollData() {
        Collection<InFlightOrder> ordersToUpdate = orderTracker.getAll();
        List<OrderPollRequest> requests = ordersToUpdate.stream()
                .map(order -> new OrderPollRequest(order.getTradingPair(), order.getClientOrderId()))
                .toList();
        if(requests.isEmpty()) return;
        requests.forEach(req ->
                CompletableFuture
                        .supplyAsync(() -> fetchOrderStatus(req.tradingPair(), req.clientOrderId()), ioExecutor)
                        .thenAcceptAsync(this::publishOrderStatus, mainExecutor)
        );
    }

    private void publishOrderStatus(OrderUpdateEvent event) {
        eventPublisher.publish(event);
    }

    private boolean isOrderNotFoundDuringStatusUpdateException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BinanceApiSpec.ORDER_NOT_EXIST_ERROR_CODE));
    }

    private OrderUpdateEvent fetchOrderStatus(String tradingPair, String clientOrderId) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(BinanceApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", clientOrderId))
                .authRequired(true)
                .build();
        try {
            JsonNode updatedOrder = restAssistant.executeRequestAndGetJsonBody(request);
            OrderState newState = BinanceApiSpec.ORDER_STATE.get(updatedOrder.get("status").asString());
            log.info("{}:{}", tradingPair, updatedOrder);
            return new OrderUpdateEvent(
                    tradingPair,
                    Instant.ofEpochMilli(updatedOrder.get("updateTime").asLong()),
                    newState,
                    clientOrderId,
                    updatedOrder.get("orderId").asString()
            );
        } catch (Exception e) {
            if (isOrderNotFoundDuringStatusUpdateException(e)) {
                // 거래소에서 주문을 못 찾음 → lost 후보
                eventPublisher.publish(new OrderLostEvent(clientOrderId));
            }
            throw e;
        }
    }

    @Override
    public void start() {
        pollScheduler.start(() -> mainExecutor.execute(this::pollData));
        running = true;
    }

    @Override
    public void stop() {
        pollScheduler.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
