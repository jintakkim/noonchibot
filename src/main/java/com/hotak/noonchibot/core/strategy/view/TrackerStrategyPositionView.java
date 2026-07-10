package com.hotak.noonchibot.core.strategy.view;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.strategy.api.StrategyPositionView;
import com.hotak.noonchibot.core.strategy.model.ExchangePosition;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class TrackerStrategyPositionView implements StrategyPositionView {
    private final Map<Exchange, PositionTracker> trackers;

    public TrackerStrategyPositionView(Map<Exchange, PositionTracker> trackers) {
        Objects.requireNonNull(trackers, "trackers");
        this.trackers = Map.copyOf(trackers);
    }

    @Override
    public Collection<ExchangePosition> positions() {
        return trackers.entrySet().stream()
                .flatMap(entry -> entry.getValue().getPositions().stream()
                        .map(position -> new ExchangePosition(entry.getKey(), position)))
                .toList();
    }
}
