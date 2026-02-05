package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.datatype.OrderType;
import com.hotak.noonchibot.core.datatype.PositionAction;
import com.hotak.noonchibot.core.event.EventListener;

import java.math.BigDecimal;
import java.time.Instant;

public interface Connector {
    List<OrderFilledEvent> getOrderFilledEvent();
    String getDisplayName();
    <T> void subscribe(Class<T> eventType, EventListener<T> listener);
    <T> void unsubscribe(Class<T> eventType, EventListener<T> listener);
    String buy(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Instant expirationTs, PositionAction positionAction);
    String sell(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Instant expirationTs, PositionAction positionAction);
    void cancel(String tradingPair, String orderId);
    boolean isReady();
    BigDecimal getAvailableBalance(String tradingPair);
    BigDecimal quantizeOrderAmount(String tradingPair, BigDecimal amount);
    //todo: amount 적절하게 quantize되는지 체크
    BigDecimal getOrderPrice(String tradingPair, boolean isBuy, BigDecimal amount);
}
