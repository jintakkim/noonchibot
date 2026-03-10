package com.hotak.noonchibot.core.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestExchangeEventPublisher implements ExchangeEventPublisher {
    private final List<ExchangeEvent> publishedEvents = new CopyOnWriteArrayList<>();

    @Override
    public <T extends ExchangeEvent> void publish(T event) {
        publishedEvents.add(event);
    }

    public List<ExchangeEvent> getPublishedEvents() {
        return publishedEvents;
    }

    public <T extends ExchangeEvent> List<T> getEventsOfType(Class<T> type) {
        return publishedEvents.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    public boolean hasEventOfType(Class<? extends ExchangeEvent> type) {
        return publishedEvents.stream().anyMatch(type::isInstance);
    }

    public void clear() {
        publishedEvents.clear();
    }
}
