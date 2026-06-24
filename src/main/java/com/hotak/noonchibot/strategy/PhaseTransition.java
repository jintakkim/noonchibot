package com.hotak.noonchibot.strategy;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PhaseTransition {
    /**
     * null 이면 현제 페이즈 유지
     */
    private final Class<?> nextPhase;
    private final Duration delay;

    public static PhaseTransition stay(Duration delay) {
        return new PhaseTransition(null, delay);
    }

    public static PhaseTransition toNextPhase(Class<?> next, Duration delay) {
        return new PhaseTransition(next, delay);
    }
}
