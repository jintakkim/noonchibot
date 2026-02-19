package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.datatype.InFlightOrder;
import com.hotak.noonchibot.core.datatype.TradeType;

import java.math.BigDecimal;
import java.util.Optional;

public interface OrderTracker {
    void startTrackingOrder(
            String orderId,
            String exchangeOrderId,
            String tradingPair,
            OrderType orderType,
            TradeType tradeType,
            BigDecimal price,
            BigDecimal amount
    );

    Optional<InFlightOrder> findActiveOrder(String orderId);
    Optional<InFlightOrder> findTrackedOrder(String orderId);
    void updateOrder(OrderUpdate orderUpdate);
    void processOrderNotFound(String orderId);

}
