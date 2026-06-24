package com.hotak.noonchibot.core.event;

/**
 * 동작중 예외 발생시 커스텀 헨들러가 필요할때
 */
public interface FailureAwareEventHandler<E extends Event> extends EventHandler<E> {
    void onFailure(E event, Throwable cause);
}