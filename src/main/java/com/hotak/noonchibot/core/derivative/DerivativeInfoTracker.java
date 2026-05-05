package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.core.event.*;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * funding-Info, leverage, position에 대한 정보를 관리
 */
@RequiredArgsConstructor
public class DerivativeInfoTracker implements LifecycleComponent {
    private final ExchangeEventSubscriber eventSubscriber;
    private PositionMode positionMode = null;
    private final Map<String, Integer> leverages = new HashMap<>();
    private final Map<String, Position> positions = new HashMap<>();
    private final Map<String, MarginMode> marginModes = new HashMap<>();

    private final EventListener<PositionUpdateEvent> positionUpdatedListener = this::onPositionUpdated;
    private final EventListener<LeverageChangedEvent> leverageChangedListener = this::onLeverageChanged;
    private final EventListener<MarginModeChangedEvent> marginModeChangedListener = this::onMarginModeChanged;
    private final EventListener<PositionModeChangedEvent> positionModeChangedListener = this::onPositionModeChanged;

    public Optional<Position> findPosition(String tradingPair, PositionSide positionSide) {
        String key = createPositionKey(tradingPair, positionSide);
        return Optional.ofNullable(positions.get(key));
    }

    public Optional<PositionMode> findPositionMode() {
        return Optional.ofNullable(positionMode);
    }

    public Optional<MarginMode> findMarginMode(String tradingPair) {
        return Optional.ofNullable(marginModes.get(tradingPair));
    }

    public Optional<Integer> findLeverage(String tradingPair) {
        return Optional.ofNullable(leverages.get(tradingPair));
    }

    private String createPositionKey(String tradingPair, PositionSide positionSide) {
        if(positionMode == PositionMode.ONEWAY) {
            return tradingPair;
        }
        return tradingPair + "_" + positionSide.name();
    }

    @VisibleForTesting
    void onPositionUpdated(PositionUpdateEvent event) {
        String key = createPositionKey(event.tradingPair(), event.positionSide());
        if (event.amount().compareTo(BigDecimal.ZERO) == 0) {
            // 포지션 종료
            positions.remove(key);
            return;
        }
        Position position = positions.get(key);
        if(position == null) {
            positions.put(key, Position.builder()
                    .tradingPair(event.tradingPair())
                    .positionSide(event.positionSide())
                    .amount(event.amount())
                    .unrealizedPnl(event.unrealizedPnl())
                    .entryPrice(event.entryPrice())
                    .build());
            return;
        }
        position.update(event.unrealizedPnl(), event.entryPrice(), event.amount());
    }

    @VisibleForTesting
    void onMarginModeChanged(MarginModeChangedEvent event) {
        marginModes.put(event.tradingPair(), event.newMode());
    }

    @VisibleForTesting
    void onPositionModeChanged(PositionModeChangedEvent event) {
        positionMode = event.newMode();
    }

    @VisibleForTesting
    void onLeverageChanged(LeverageChangedEvent event) {
        leverages.put(event.tradingPair(), event.leverage());
    }

    @Override
    public void start() {
        eventSubscriber.subscribe(PositionUpdateEvent.class, positionUpdatedListener);
        eventSubscriber.subscribe(LeverageChangedEvent.class, leverageChangedListener);
        eventSubscriber.subscribe(MarginModeChangedEvent.class, marginModeChangedListener);
        eventSubscriber.subscribe(PositionModeChangedEvent.class, positionModeChangedListener);
    }

    @Override
    public void shutdown() {
        eventSubscriber.unsubscribe(PositionUpdateEvent.class, positionUpdatedListener);
        eventSubscriber.unsubscribe(LeverageChangedEvent.class, leverageChangedListener);
        eventSubscriber.unsubscribe(MarginModeChangedEvent.class, marginModeChangedListener);
        eventSubscriber.unsubscribe(PositionModeChangedEvent.class, positionModeChangedListener);
    }
}
