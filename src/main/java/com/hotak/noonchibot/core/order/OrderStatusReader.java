package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.event.internal.order.OrderEvent;

public interface OrderStatusReader {
    OrderEvent.StatusReceived fetch(String tradingPair, String clientOrderId);
}
