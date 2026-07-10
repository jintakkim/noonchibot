package com.hotak.noonchibot.core.strategy.view;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.strategy.api.StrategyOrderView;
import com.hotak.noonchibot.core.strategy.model.ExchangeOrderView;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class TrackerStrategyOrderView implements StrategyOrderView {
    private final Map<Exchange, OrderTracker> trackers;

    public TrackerStrategyOrderView(Map<Exchange, OrderTracker> trackers) {
        Objects.requireNonNull(trackers, "trackers");
        this.trackers = Map.copyOf(trackers);
    }

    @Override
    public Collection<ExchangeOrderView> openOrders() {
        return trackers.entrySet().stream()
                .flatMap(entry -> entry.getValue().getAllInFlightOrders().stream()
                        .map(order -> new ExchangeOrderView(entry.getKey(), order.toView())))
                .filter(order -> !order.order().state().isTerminal())
                .toList();
    }
}
