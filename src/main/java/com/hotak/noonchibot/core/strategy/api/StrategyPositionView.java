package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.model.ExchangePosition;

import java.util.Collection;

public interface StrategyPositionView {
    Collection<ExchangePosition> positions();
}
