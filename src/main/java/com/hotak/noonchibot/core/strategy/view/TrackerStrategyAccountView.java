package com.hotak.noonchibot.core.strategy.view;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class TrackerStrategyAccountView implements StrategyAccountView {
    private final Map<Exchange, AccountBalanceTracker> trackers;

    public TrackerStrategyAccountView(Map<Exchange, AccountBalanceTracker> trackers) {
        Objects.requireNonNull(trackers, "trackers");
        this.trackers = Map.copyOf(trackers);
    }

    @Override
    public Optional<BigDecimal> availableBalance(Exchange exchange, String asset) {
        AccountBalanceTracker tracker = trackers.get(exchange);
        if (tracker == null || !tracker.isInitialized()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tracker.getAvailableBalance(asset));
    }
}
