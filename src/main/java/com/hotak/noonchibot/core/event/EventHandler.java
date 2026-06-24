package com.hotak.noonchibot.core.event;

/**
 * 동작 중 예외가 밖으로 던져지면 에러 로그 발생.
 * 예외시 이벤트, 동작이 필요한 경우 FailureAwareEventHandler를 구현
 */
@FunctionalInterface
public interface EventHandler<E extends Event> {
    void onEvent(E event);
}
