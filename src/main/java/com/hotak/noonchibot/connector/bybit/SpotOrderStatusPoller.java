package com.hotak.noonchibot.connector.bybit;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
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
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
class SpotOrderStatusPoller implements LifecycleComponent {
    private final RestAssistant restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final OrderTracker orderTracker;
    private final MainExecutor mainExecutor;
    private final IoExecutor ioExecutor;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final PollScheduler pollScheduler;

    public SpotOrderStatusPoller(
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
        List<SpotOrderStatusPoller.OrderPollRequest> requests = ordersToUpdate.stream()
                .map(order -> new SpotOrderStatusPoller.OrderPollRequest(order.getTradingPair(), order.getClientOrderId()))
                .toList();
        if(requests.isEmpty()) return;
        requests.forEach(req ->
                CompletableFuture
                        .supplyAsync(() -> fetchOrderStatus(req.tradingPair(), req.clientOrderId()), ioExecutor)
                        .thenAccept(this::publishOrderStatus)
        );
    }

    private void publishOrderStatus(OrderUpdateEvent event) {
        if (event == null) return;
        eventPublisher.publish(event);
    }

    private boolean isOrderNotFoundDuringStatusUpdateException(Exception e) {
        String message = e.getMessage();
        return message != null
                && message.contains(String.valueOf(SpotApiSpec.ORDER_NOT_EXIST_ERROR_CODE));
    }

    private OrderUpdateEvent fetchOrderStatus(String tradingPair, String clientOrderId) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(SpotApiSpec.ORDER_REALTIME_PATH_URL)
                .params(Map.of(
                        "category", "spot",
                        "symbol", symbol,
                        "orderLinkId", clientOrderId
                ))
                .authRequired(true)
                .build();
        try {
            JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);
            JsonNode list = response.get("result").get("list");
            if (list.isEmpty()) {
                // realtime에 없다면 이미 체결/취소되어 사라졌거나 존재하지 않는 주문이다
                eventPublisher.publish(new OrderLostEvent(clientOrderId));
                return null;
            }
            JsonNode updatedOrder = list.get(0);
            OrderState newState = SpotApiSpec.ORDER_STATE.get(updatedOrder.get("orderStatus").asString());
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
    }

    @Override
    public void shutdown() {
        pollScheduler.stop();
    }
}