package com.hotak.noonchibot.core.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * thread safe
 */
@RequiredArgsConstructor
public class SimpleEventLogger implements EventLogger {
    @Getter
    private final String eventSource;

    /**
     * 일반 이벤트는 메모리 관리를 위해 최근 MAX_GENERIC_EVENTS 개만 유지
     */
    private final Deque<Object> genericLoggedEvents = new ConcurrentLinkedDeque<>();
    private static final int MAX_GENERIC_EVENTS = 50;

    /**
     * 체결 이벤트: PnL 계산을 위해 전부 보관
     */
    private final Deque<OrderFilledEvent> orderFilledLoggedEvents = new ConcurrentLinkedDeque<>();

    /**
     * 특정 이벤트 대기용
     */
    private final Map<CompletableFuture<?>, Class<?>> waiting = new ConcurrentHashMap<>();

    @Override
    public void onEvent(Object event) {
        if (event instanceof OrderFilledEvent orderFilledEvent) {
            orderFilledLoggedEvents.add(orderFilledEvent);
        } else {
            genericLoggedEvents.add(event);
            // 최대 크기 유지
            while (genericLoggedEvents.size() > MAX_GENERIC_EVENTS) {
                genericLoggedEvents.pollFirst();
            }
        }
        notifyWaiters(event);
    }

    private void notifyWaiters(Object event) {
        Class<?> eventType = event.getClass();
        waiting.forEach((future, waitingType) -> {
            if (waitingType.isAssignableFrom(eventType)) {
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> objectFuture = (CompletableFuture<Object>) future;
                objectFuture.complete(event);
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> waitFor(Class<T> eventType, Duration timeout) {
        CompletableFuture<T> future = new CompletableFuture<>();
        waiting.put(future, eventType);

        // 타임아웃 처리
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((result, ex) -> waiting.remove(future));
        return future;
    }

    @Override
    public List<Object> getEventLog() {
        List<Object> combined = new ArrayList<>(genericLoggedEvents);
        combined.addAll(orderFilledLoggedEvents);
        return combined;
    }

    @Override
    public void clear() {
        genericLoggedEvents.clear();
        orderFilledLoggedEvents.clear();
    }
}
