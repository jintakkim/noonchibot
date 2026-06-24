package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.BinanceResponseCodeParser;
import com.hotak.noonchibot.connector.binance.DerivativeApiSpec;
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
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
class DerivativeInfoDataSource implements LifecycleAware {
    final FailureAwareEventHandler<PositionModeChangeEvent.IORequested> positionModeChangeHandler;
    final FailureAwareEventHandler<LeverageChangeEvent.IORequested> leverageChangeHandler;
    final FailureAwareEventHandler<MarginModeChangeEvent.IORequested> marginModeChangeHandler;
    private final EventSubscriber eventSubscriber;
    private final Set<Subscription> subscriptions = new HashSet<>();

    public DerivativeInfoDataSource(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        this.positionModeChangeHandler = new PositionModeChangeHandler(eventPublisher, restAssistant);
        this.leverageChangeHandler = new LeverageChangeHandler(tradingPairSymbolRegistry, restAssistant, eventPublisher);
        this.marginModeChangeHandler = new MarginModeChangeHandler(tradingPairSymbolRegistry, restAssistant, eventPublisher);
        this.eventSubscriber = eventSubscriber;
    }

    @Override
    public int phase() {
        return Phases.DERIVATIVE_INFO_SETUP;
    }

    @Override
    public void onStart() {
        subscriptions.add(
                eventSubscriber.subscribe(
                    PositionModeChangeEvent.IORequested.class,
                        positionModeChangeHandler,
                    ExecutionPolicy.concurrent()
                ));
        subscriptions.add(
                eventSubscriber.subscribe(
                        LeverageChangeEvent.IORequested.class,
                        leverageChangeHandler,
                        ExecutionPolicy.concurrent()
                ));
        subscriptions.add(
                eventSubscriber.subscribe(
                        MarginModeChangeEvent.IORequested.class,
                        marginModeChangeHandler,
                        ExecutionPolicy.concurrent()
                ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    private record PositionModeChangeHandler(
            EventPublisher eventPublisher,
            RestAssistant restAssistant
    ) implements FailureAwareEventHandler<PositionModeChangeEvent.IORequested> {
        @Override
        public void onEvent(PositionModeChangeEvent.IORequested req) {
            JsonNode res = restAssistant.executeRequestAndGetJsonBody(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(DerivativeApiSpec.POSITION_MODE_PATH_URL)
                            .params(Map.of(
                                    "dualSidePosition", req.wantTo() == PositionMode.HEDGE
                            ))
                            .authRequired(true)
                            .throwError(false)
                            .build()
            );
            int code = BinanceResponseCodeParser.parseCode(res);
            if (code != DerivativeApiSpec.Code.SUCCESS && code != DerivativeApiSpec.Code.NO_NEED_TO_CHANGE_POSITION_SIDE) {
                throw new IllegalStateException("Position mode change failed: " + res);
            }
            log.debug("Position mode applied: {}", req.wantTo());
            eventPublisher.publish(new PositionModeChangeEvent.Applied(req.wantTo()));
        }

        @Override
        public void onFailure(PositionModeChangeEvent.IORequested req, Throwable cause) {
            eventPublisher.publish(new PositionModeChangeEvent.Failed(cause));
        }
    }

    private record LeverageChangeHandler(
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            EventPublisher eventPublisher
    ) implements FailureAwareEventHandler<LeverageChangeEvent.IORequested> {
        @Override
        public void onEvent(LeverageChangeEvent.IORequested req) {
            String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(req.tradingPair());
            restAssistant.executeRequestAndGetResponse(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(DerivativeApiSpec.LEVERAGE_PATH_URL)
                            .params(Map.of(
                                    "symbol", exchangeSymbol,
                                    "leverage", req.wantTo()
                            ))
                            .authRequired(true)
                            .build()
            );
            log.debug("Leverage applied: {} -> {}x", req.tradingPair(), req.wantTo());
            eventPublisher.publish(new LeverageChangeEvent.Applied(req.tradingPair(), req.wantTo()));
        }

        @Override
        public void onFailure(LeverageChangeEvent.IORequested req, Throwable cause) {
            eventPublisher.publish(new LeverageChangeEvent.Failed(cause));
        }
    }

    private record MarginModeChangeHandler(
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            RestAssistant restAssistant,
            EventPublisher eventPublisher
    ) implements FailureAwareEventHandler<MarginModeChangeEvent.IORequested> {
        @Override
        public void onEvent(MarginModeChangeEvent.IORequested req) {
            String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(req.tradingPair());
            String marginType = switch (req.wantTo()) {
                case MarginMode.CROSS -> "CROSSED";
                case MarginMode.ISOLATED -> "ISOLATED";
            };
            JsonNode res = restAssistant.executeRequestAndGetJsonBody(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(DerivativeApiSpec.MARGIN_TYPE_PATH_URL)
                            .params(Map.of(
                                    "symbol", exchangeSymbol,
                                    "marginType", marginType
                            ))
                            .authRequired(true)
                            .throwError(false)
                            .build()
            );
            int code = BinanceResponseCodeParser.parseCode(res);
            if (code != DerivativeApiSpec.Code.SUCCESS && code != DerivativeApiSpec.Code.NO_NEED_TO_CHANGE_MARGIN_TYPE) {
                throw new IllegalStateException("Margin mode change failed: " + res);
            }
            log.debug("Margin mode applied: {} -> {}", req.tradingPair(), req.wantTo());
            eventPublisher.publish(new MarginModeChangeEvent.Applied(req.tradingPair(), req.wantTo()));
        }

        @Override
        public void onFailure(MarginModeChangeEvent.IORequested req, Throwable cause) {
            eventPublisher.publish(new MarginModeChangeEvent.Failed(cause));
        }
    }
}