package com.hotak.noonchibot.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class TestEventPublisher implements EventPublisher {
    private final List<EventEnvelope<?>> envelopes = new ArrayList<>();

    @Override
    public void publish(Event event) {
        publish(event, EventContext.currentOrRoot().derive());
    }

    @Override
    public void publish(Event event, EventMetadata metadata) {
        envelopes.add(new EventEnvelope<>(event, metadata));
    }

    public List<EventEnvelope<?>> getEnvelopes() {
        return List.copyOf(envelopes);
    }

    public List<Event> getEvents() {
        return envelopes.stream()
                .map(env -> (Event) env.payload())
                .toList();
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> List<T> getEventsOfType(Class<T> type) {
        return envelopes.stream()
                .map(EventEnvelope::payload)
                .filter(type::isInstance)
                .map(e -> (T) e)
                .toList();
    }

    public  <T extends Event> T only(Class<T> type) {
        List<T> events = getEventsOfType(type);
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> List<EventEnvelope<T>> getEnvelopesOfType(Class<T> type) {
        return envelopes.stream()
                .filter(env -> type.isInstance(env.payload()))
                .map(env -> (EventEnvelope<T>) env)
                .toList();
    }

    public EventEnvelope<?> getLastEnvelope() {
        if (envelopes.isEmpty()) throw new IllegalStateException("No events published");
        return envelopes.get(envelopes.size() - 1);
    }

    public <T extends Event> Optional<T> getFirstEventOfType(Class<T> type) {
        return getEventsOfType(type).stream().findFirst();
    }

    public boolean hasEventOfType(Class<? extends Event> type) {
        return envelopes.stream().anyMatch(env -> type.isInstance(env.payload()));
    }

    public int countEventsOfType(Class<? extends Event> type) {
        return (int) envelopes.stream()
                .filter(env -> type.isInstance(env.payload()))
                .count();
    }

    public int totalCount() {
        return envelopes.size();
    }

    public void clear() {
        envelopes.clear();
    }
}
