package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.order.OrderView;

import java.util.Collection;
import java.util.Optional;

public interface StrategyOrderView {
    Optional<OrderView> findByClientOrderId(String clientOrderId);

    Collection<OrderView> openOrders();
}
