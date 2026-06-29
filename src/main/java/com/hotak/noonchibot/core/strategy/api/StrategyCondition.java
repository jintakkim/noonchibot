package com.hotak.noonchibot.core.strategy.api;

import java.util.Objects;

@FunctionalInterface
public interface StrategyCondition<C> {
    boolean matches(C context);

    default StrategyCondition<C> and(StrategyCondition<C> other) {
        Objects.requireNonNull(other, "other");
        return context -> matches(context) && other.matches(context);
    }

    default StrategyCondition<C> or(StrategyCondition<C> other) {
        Objects.requireNonNull(other, "other");
        return context -> matches(context) || other.matches(context);
    }

    default StrategyCondition<C> negate() {
        return context -> !matches(context);
    }

    static <C> StrategyCondition<C> always() {
        return context -> true;
    }

    static <C> StrategyCondition<C> never() {
        return context -> false;
    }
}
