package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestErrorAction;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
class OrderStatusDataSource implements EventHandler<OrderEvent.StatusUpdateRequested>, LifecycleAware {

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final RestAssistant restAssistant;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    public OrderStatusDataSource(
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                tradingPairSymbolRegistry,
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderStatus(Exchange.BINANCE_SPOT))
                        .errorClassifier(new BinanceExchangeErrorClassifier())
                        .on4xxError(error -> error.code() != null
                                && error.code() == ApiSpec.ORDER_NOT_EXIST_ERROR_CODE
                                ? RestErrorAction.IGNORE_AS_REJECTED
                                : RestErrorAction.DEFAULT)
                        .maxRetry(2)
                        .build(),
                eventPublisher,
                eventSubscriber
        );
    }

    @Override
    public void onEvent(OrderEvent.StatusUpdateRequested event) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(event.tradingPair());
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(ApiSpec.ORDER_PATH_URL)
                .params(Map.of("symbol", symbol, "origClientOrderId", event.clientOrderId()))
                .authRequired(true)
                .build();

        JsonNode updatedOrder = restAssistant.executeRequestAndGetJsonBody(request);
        OrderState newState = ApiSpec.ORDER_STATE.get(updatedOrder.get("status").asString());
        eventPublisher.publish(new OrderEvent.StatusReceived(
                event.tradingPair(),
                event.clientOrderId(),
                updatedOrder.get("orderId").asString(),
                newState,
                Instant.ofEpochMilli(updatedOrder.get("updateTime").asLong())
        ));
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                OrderEvent.StatusUpdateRequested.class,
                this,
                ExecutionPolicy.concurrent()
        );

    }

    @Override
    public void onShutdown() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public int phase() {
        return Phases.ORDER_STATUS_DATASOURCE_SETUP;
    }
}
