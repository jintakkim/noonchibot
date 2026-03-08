package com.hotak.noonchibot.core.event;

public interface ExchangeEventPublisher {
    <T extends ExchangeEvent> void publish(T event);
}
