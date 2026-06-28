package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderView;

import java.util.Objects;

public record VenueOrderView(
        Exchange exchange,
        OrderView order
) {
    public VenueOrderView {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(order, "order");
    }
}
