package com.hotak.noonchibot.connector.binance.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.NoChangeRequiredException;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.derivative.MarginMode;
import com.hotak.noonchibot.core.derivative.PositionMode;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.derivative.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
class DerivativeAccountCommandExecutor implements LifecycleAware {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final Set<Subscription> subscriptions = new HashSet<>();

    public DerivativeAccountCommandExecutor(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.eventPublisher = eventPublisher;
        this.eventSubscriber = eventSubscriber;
    }

    @Override
    public int phase() {
        return Phases.DERIVATIVE_ACCOUNT_COMMAND_EXECUTOR_SETUP;
    }

    @Override
    public void onStart() {
        subscriptions.add(
                eventSubscriber.subscribe(
                    PositionModeChangeIORequestedEvent.class,
                        this::changePositionMode,
                    ExecutionPolicy.concurrent()
                ));
        subscriptions.add(
                eventSubscriber.subscribe(
                        LeverageChangeIORequestedEvent.class,
                        this::changeLeverage,
                        ExecutionPolicy.concurrent()
                ));
        subscriptions.add(
                eventSubscriber.subscribe(
                        MarginModeChangeIORequestedEvent.class,
                        this::changeMarginMode,
                        ExecutionPolicy.concurrent()
                ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @VisibleForTesting
    void changePositionMode(PositionModeChangeIORequestedEvent request) {
        try {
            restAssistant.executeRequestAndGetResponse(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(ApiSpec.POSITION_MODE_PATH_URL)
                            .params(Map.of(
                                    "dualSidePosition", request.wantTo() == PositionMode.HEDGE
                            ))
                            .authRequired(true)
                            .build()
            );
        } catch (NoChangeRequiredException ignored) {
            // The requested mode is already active.
        }
        log.debug("Position mode applied: {}", request.wantTo());
        eventPublisher.publish(new PositionModeChangeAppliedEvent(request.wantTo()));
    }

    @VisibleForTesting
    void changeLeverage(LeverageChangeIORequestedEvent request) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(request.tradingPair());
        restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.LEVERAGE_PATH_URL)
                        .params(Map.of(
                                "symbol", exchangeSymbol,
                                "leverage", request.wantTo()
                        ))
                        .authRequired(true)
                        .build()
        );
        log.debug("Leverage applied: {} -> {}x", request.tradingPair(), request.wantTo());
        eventPublisher.publish(new LeverageChangeAppliedEvent(request.tradingPair(), request.wantTo()));
    }

    @VisibleForTesting
    void changeMarginMode(MarginModeChangeIORequestedEvent request) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(request.tradingPair());
        String marginType = switch (request.wantTo()) {
            case MarginMode.CROSS -> "CROSSED";
            case MarginMode.ISOLATED -> "ISOLATED";
        };
        try {
            restAssistant.executeRequestAndGetResponse(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(ApiSpec.MARGIN_TYPE_PATH_URL)
                            .params(Map.of(
                                    "symbol", exchangeSymbol,
                                    "marginType", marginType
                            ))
                            .authRequired(true)
                            .build()
            );
        } catch (NoChangeRequiredException ignored) {
            // The requested mode is already active.
        }
        log.debug("Margin mode applied: {} -> {}", request.tradingPair(), request.wantTo());
        eventPublisher.publish(new MarginModeChangeAppliedEvent(request.tradingPair(), request.wantTo()));
    }
}
