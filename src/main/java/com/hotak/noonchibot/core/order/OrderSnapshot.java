package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.trade.TradeType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class OrderSnapshot {
    @Id
    private String clientOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Exchange exchange;
    @Column(nullable = false)
    private String tradingPair;
    @Enumerated(EnumType.STRING)
    private OrderType orderType;
    @Enumerated(EnumType.STRING)
    private TradeType tradeType;
    @Enumerated(EnumType.STRING)
    private TimeInForce timeInForce;
    private boolean postOnly;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderState state;
    private BigDecimal amount;
    private BigDecimal price;
    private String exchangeOrderId;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderSnapshot from(Exchange exchange, OrderView order) {
        OrderSnapshot snapshot = new OrderSnapshot();
        snapshot.clientOrderId = order.clientOrderId();
        snapshot.exchange = exchange;
        snapshot.tradingPair = order.tradingPair();
        snapshot.orderType = order.orderType();
        snapshot.tradeType = order.tradeType();
        snapshot.timeInForce = order.timeInForce();
        snapshot.postOnly = order.postOnly();
        snapshot.state = order.state();
        snapshot.amount = order.amount();
        snapshot.price = order.price();
        snapshot.exchangeOrderId = order.exchangeOrderId();
        snapshot.createdAt = order.createdAt();
        snapshot.updatedAt = order.updatedAt();
        return snapshot;
    }

    public void update(OrderView order) {
        exchangeOrderId = order.exchangeOrderId();
        state = order.state();
        updatedAt = order.updatedAt();
    }

    public InFlightOrder toInFlightOrder() {
        return InFlightOrder.restore(new OrderView(
                clientOrderId,
                exchangeOrderId,
                tradingPair,
                orderType,
                tradeType,
                timeInForce,
                postOnly,
                state,
                amount,
                price,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                amount,
                new HashSet<>(),
                new HashMap<>(),
                createdAt,
                updatedAt
        ));
    }
}
