package com.hotak.noonchibot.core.strategy.model;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.Position;

import java.util.Objects;

public record VenuePosition(
        Exchange exchange,
        Position position
) {
    public VenuePosition {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(position, "position");
    }
}
