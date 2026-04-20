package com.hotak.noonchibot.connector.bybit;

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
import com.hotak.noonchibot.core.event.OrderUpdateEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class BybitOrderStatusPoller implements SmartLifecycle {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final OrderTracker orderTracker;
    private final MainExecutor mainExecutor;
    private final IoExecutor ioExecutor;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final PollScheduler pollScheduler;
    private final String orderPathUrl;
    private volatile boolean running = false;

    public BybitOrderStatusPoller(
            RestAssistant restAssistant,
            ExchangeEventPublisher eventPublisher,
            OrderTracker orderTracker,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            WebsocketStatus websocketStatus,
            TaskScheduler scheduler,
            String orderPathUrl
    ) {
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.orderTracker = orderTracker;
        this.mainExecutor = mainExecutor;
        this.ioExecutor = ioExecutor;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.orderPathUrl = orderPathUrl;
        this.pollScheduler = new PollScheduler(websocketStatus, scheduler);
    }

    @VisibleForTesting
    void pollData() {
        Collection<InFlightOrder> ordersToUpdate = orderTracker.getAll();
        if (ordersToUpdate.isEmpty()) return;
        ordersToUpdate.forEach(order ->
                CompletableFuture
                        .supplyAsync(() -> fetchOrderStatus(order), ioExecutor)
                        .thenAccept(eventPublisher::publish)
        );
    }

    private boolean isOrderNotFoundDuringStatusUpdateException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(BybitApiSpec.ORDER_NOT_EXIST_ERROR_CODE));
    }

    private OrderUpdateEvent fetchOrderStatus(InFlightOrder order) {
        String tradingPair = order.getTradingPair();
        String clientOrderId = order.getClientOrderId();
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(orderPathUrl)
                .params(Map.of(
                        "category", "linear",
                        "symbol", symbol,
                        "orderLinkId", clientOrderId
                ))
                .authRequired(true)
                .build();
        try {
            JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);
            JsonNode updatedOrder = response.get("result").get("list").get(0);
            OrderState newState = BybitApiSpec.ORDER_STATE.get(updatedOrder.get("orderStatus").asString());
            log.info("{}:{}", tradingPair, updatedOrder);
            return new OrderUpdateEvent(
                    tradingPair,
                    Instant.ofEpochMilli(updatedOrder.get("updatedTime").asLong()),
                    newState,
                    clientOrderId,
                    updatedOrder.get("orderId").asString(),
                    null
            );
        } catch (Exception e) {
            if (isOrderNotFoundDuringStatusUpdateException(e)) {
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