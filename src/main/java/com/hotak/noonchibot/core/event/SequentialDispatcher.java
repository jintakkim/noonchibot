package com.hotak.noonchibot.core.event;

@FunctionalInterface
public interface SequentialDispatcher {
    void dispatchSequential(Runnable task);
}
