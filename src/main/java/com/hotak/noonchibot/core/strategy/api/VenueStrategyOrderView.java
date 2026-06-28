package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.model.VenueOrderView;

import java.util.Collection;

public interface VenueStrategyOrderView {
    Collection<VenueOrderView> openOrders();
}
