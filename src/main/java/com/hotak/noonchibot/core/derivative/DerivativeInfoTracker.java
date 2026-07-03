package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.derivative.LeverageChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.MarginModeChangeEvent;
import com.hotak.noonchibot.core.event.internal.derivative.PositionModeChangeEvent;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class DerivativeInfoTracker implements LifecycleAware {
    private final EventSubscriber eventSubscriber;
    private PositionMode positionMode = null;
    private final Map<String, Integer> leverages = new HashMap<>();
    private final Map<String, MarginMode> marginModes = new HashMap<>();
    private final Set<Subscription> subscriptions = new HashSet<>();

    public Optional<PositionMode> findPositionMode() {
        return Optional.ofNullable(positionMode);
    }

    public Optional<MarginMode> findMarginMode(String tradingPair) {
        return Optional.ofNullable(marginModes.get(tradingPair));
    }

    public Optional<Integer> findLeverage(String tradingPair) {
        return Optional.ofNullable(leverages.get(tradingPair));
    }

    @VisibleForTesting
    void onMarginModeChanged(MarginModeChangeEvent.Applied event) {
        marginModes.put(event.tradingPair(), event.changedTo());
    }

    @VisibleForTesting
    void onPositionModeChanged(PositionModeChangeEvent.Applied event) {
        positionMode = event.changedTo();
    }

    @VisibleForTesting
    void onLeverageChanged(LeverageChangeEvent.Applied event) {
        leverages.put(event.tradingPair(), event.changedTo());
    }

    @Override
    public void onStart() {
        subscriptions.add(eventSubscriber.subscribe(
                PositionModeChangeEvent.Applied.class,
                this::onPositionModeChanged ,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                LeverageChangeEvent.Applied.class,
                this::onLeverageChanged,
                ExecutionPolicy.sequential()
        ));
        subscriptions.add(eventSubscriber.subscribe(
                MarginModeChangeEvent.Applied.class,
                this::onMarginModeChanged,
                ExecutionPolicy.sequential()
        ));

    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
    }

    @Override
    public int phase() {
        return Phases.DERIVATIVE_INFO_TRACKER_SETUP;
    }
}
