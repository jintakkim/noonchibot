package com.hotak.noonchibot.core.event;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EventLogger {
    List<Object> getEventLog();
    String getEventSource();
    void onEvent(Object event);
    /**
     * 특정 타입의 이벤트가 발생할 때까지 대기
     *
     * @param eventType 대기할 이벤트 타입
     * @param timeout 타임아웃
     * @param <T> 이벤트 타입
     * @return 발생한 이벤트
     */
    <T> CompletableFuture<T> waitFor(Class<T> eventType, Duration timeout);

    /**
     * 기본 타임아웃(180초)으로 이벤트 대기
     */
    default <T> CompletableFuture<T> waitFor(Class<T> eventType) {
        return waitFor(eventType, Duration.ofMinutes(3));
    }

    void clear();
}
