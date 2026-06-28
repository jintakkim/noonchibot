package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.strategy.model.ExchangeOrderView;

import java.util.Collection;

public interface StrategyOrderView {
    Collection<ExchangeOrderView> openOrders();
}
