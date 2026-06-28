package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.derivative.Position;

import java.util.Collection;

public interface StrategyPositionView {
    Collection<Position> positions();
}
