package com.hotak.noonchibot.core.order;

import java.util.Set;

public interface OrderClient {
    OrderPlaceResult placeOrder(InFlightOrder order);
    OrderCancelResult cancelOrder(String tradingPair, String clientOrderId);
    Set<TimeInForce> getSupportedTimeInForce();
}
