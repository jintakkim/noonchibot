package com.hotak.noonchibot.connector.hyperliquid;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderLostEvent;
import com.hotak.noonchibot.core.order.OrderUpdateDto;
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
class DerivativeOrderStatusPoller implements LifecycleComponent {
    private final RestAssistantImpl restAssistant;
    private final ExchangeEventPublisher eventPublisher;
    private final OrderTracker orderTracker;
    private final MainExecutor mainExecutor;
    private final IoExecutor ioExecutor;
    private final String userAddress;
    private final PollScheduler pollScheduler;

    public DerivativeOrderStatusPoller(
            RestAssistantImpl restAssistant,
            ExchangeEventPublisher eventPublisher,
            OrderTracker orderTracker,
            MainExecutor mainExecutor,
            IoExecutor ioExecutor,
            String userAddress,
            WebsocketStatus websocketStatus,
            TaskScheduler scheduler
    ) {
        this.restAssistant = restAssistant;
        this.eventPublisher = eventPublisher;
        this.orderTracker = orderTracker;
        this.mainExecutor = mainExecutor;
        this.ioExecutor = ioExecutor;
        this.userAddress = userAddress;
        this.pollScheduler = new PollScheduler(websocketStatus, scheduler);
    }

    private record OrderPollRequest(String tradingPair, String clientOrderId) {}

    @VisibleForTesting
    CompletableFuture<Void> pollData() {
        Collection<InFlightOrder> ordersToUpdate = orderTracker.getAll();
        List<OrderPollRequest> requests = ordersToUpdate.stream()
                .map(order -> new OrderPollRequest(order.getTradingPair(), order.getClientOrderId()))
                .toList();
        if (requests.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = requests.stream()
                .map(req -> CompletableFuture
                        .supplyAsync(() -> fetchOrderStatus(req.tradingPair(), req.clientOrderId()), ioExecutor)
                        .thenAccept(this::publishOrderStatus)
                        .exceptionally(e -> {
                            log.error("Order status polling error for {}/{}",
                                    req.tradingPair(), req.clientOrderId(), e);
                            return null;
                        })
                )
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    private void publishOrderStatus(OrderUpdateDto event) {
        if (event != null) {
            eventPublisher.publish(event);
        }
    }

    private OrderUpdateDto fetchOrderStatus(String tradingPair, String clientOrderId) {
        Map<String, Object> body = Map.of(
                "type", "orderStatus",
                "user", userAddress,
                "oid", clientOrderId
        );

        RestRequest request = RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(body)
                .authRequired(false)
                .build();

        JsonNode response = restAssistant.executeRequestAndGetJsonBody(request);
        String status = response.get("status").asString();

        // 주문을 못 찾은 경우 — LOST 처리
        if ("unknownOid".equals(status)) {
            log.warn("Order not found on exchange: tradingPair={}, cloid={}", tradingPair, clientOrderId);
            eventPublisher.publish(new OrderLostEvent(clientOrderId));
            return null;
        }

        // 정상 응답: status == "order"
        if (!"order".equals(status)) {
            log.warn("Unexpected orderStatus response: status={}, cloid={}", status, clientOrderId);
            return null;
        }

        JsonNode orderWrapper = response.get("order");
        JsonNode orderNode = orderWrapper.get("order");
        String orderStatusStr = orderWrapper.get("status").asString();
        long statusTimestamp = orderWrapper.get("statusTimestamp").asLong();

        OrderState newState = DerivativeApiSpec.ORDER_STATE.get(orderStatusStr);
        long oid = orderNode.get("oid").asLong();

        log.debug("{}:{} -> {}", tradingPair, clientOrderId, orderStatusStr);
        return new OrderUpdateDto(
                tradingPair,
                Instant.ofEpochMilli(statusTimestamp),
                newState,
                clientOrderId,
                String.valueOf(oid)
        );
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
