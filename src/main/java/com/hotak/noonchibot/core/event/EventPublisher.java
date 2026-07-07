package com.hotak.noonchibot.core.event;

public interface EventPublisher {
    EventPublisher NOOP = new EventPublisher() {
        @Override
        public void publish(Event event) {}

        @Override
        public void publish(Event event, EventMetadata metadata) {}
    };

    void publish(Event event);
    void publish(Event event, EventMetadata metadata);
}
