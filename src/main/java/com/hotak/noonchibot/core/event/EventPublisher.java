package com.hotak.noonchibot.core.event;

public interface EventPublisher {
    void publish(Event event);
    void publish(Event event, EventMetadata metadata);
}
