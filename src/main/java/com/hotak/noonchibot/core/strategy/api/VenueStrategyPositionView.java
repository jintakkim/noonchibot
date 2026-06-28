package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.model.VenuePosition;

import java.util.Collection;

public interface VenueStrategyPositionView {
    Collection<VenuePosition> positions();
}
