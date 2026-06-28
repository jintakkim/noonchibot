package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderView;

import java.util.Objects;

public record ExchangeOrderView(
        Exchange exchange,
        OrderView order
) {
    public ExchangeOrderView {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(order, "order");
    }
}
