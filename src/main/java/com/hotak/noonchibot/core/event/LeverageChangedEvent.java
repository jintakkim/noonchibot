package com.hotak.noonchibot.core.event;

public record LeverageChangedEvent(
        String tradingPair, int leverage
) implements ExchangeEvent {
}
