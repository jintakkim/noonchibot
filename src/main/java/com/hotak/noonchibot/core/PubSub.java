package com.hotak.noonchibot.core;

import com.hotak.noonchibot.core.event.EventListener;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * todo: weakReference implementation
 */

@Slf4j
public class PubSub {
    private final Map<Class<?>, Set<EventListener<?>>> listenerRegistry = new ConcurrentHashMap<>();

    public <T> void addListener(Class<T> eventType, EventListener<T> listener) {
        listenerRegistry.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet()).add(listener);
    }

    public <T> void removeListener(Class<T> eventType, EventListener<T> listener) {
        Set<EventListener<?>> listeners = listenerRegistry.get(eventType);
        if (listeners == null) return;
        listeners.remove(listener);
        if (listeners.isEmpty()) listenerRegistry.remove(eventType, listeners);
    }

    @SuppressWarnings("unchecked")
    public <T> void triggerEvent(T event) {
        Set<EventListener<?>> listeners = listenerRegistry.get(event.getClass());
        if (listeners == null) return;
        for (EventListener<?> listener : new HashSet<>(listeners)) {
            try {
                ((EventListener<T>) listener).call(event);
            } catch (Exception e) {
                log.error("Error in event listener. type={}, event={}", event.getClass().getSimpleName(), event, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Set<EventListener<T>> getEventListeners(Class<T> eventType) {
        Set<EventListener<?>> listeners = listenerRegistry.get(eventType);
        if(listeners == null) return Collections.emptySet();
        return listeners.stream()
                .map(l -> (EventListener<T>) l)
                .collect(Collectors.toSet());
    }
}


