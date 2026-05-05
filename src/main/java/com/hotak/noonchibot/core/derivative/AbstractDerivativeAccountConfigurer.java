package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.MarginModeChangedEvent;
import com.hotak.noonchibot.core.event.LeverageChangedEvent;
import com.hotak.noonchibot.core.event.PositionModeChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 래버리지 설정, 포지션 모드 설정(HEDGE, ONE-WAY), 마진 모드 설정(CROSS, ISOLATED)
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractDerivativeAccountConfigurer implements DerivativeAccountConfigurer {
    private final DerivativeInfoTracker tracker;
    private final ExchangeEventPublisher eventPublisher;

    @Override
    public CompletableFuture<Void> ensurePositionMode(PositionMode desired) {
        Optional<PositionMode> current = tracker.findPositionMode();
        if(desired == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Desired position mode cannot be null"));
        }
        if(current.isPresent() && current.get() == desired) {
            log.debug("position mode already set to {}, skipping", desired);
            return CompletableFuture.completedFuture(null);
        }
        log.info("changing position mode: {} -> {}", current, desired);
        return applyPositionMode(desired)
                .thenRun(() -> eventPublisher.publish(new PositionModeChangedEvent(desired)));
    }

    @Override
    public CompletableFuture<Void> ensureLeverage(String tradingPair, int desired) {
        if (desired <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("leverage must be positive, got: " + desired));
        }
        Optional<Integer> current = tracker.findLeverage(tradingPair);
        if(current.isPresent() && current.get() == desired) {
            log.debug("leverage for {} already {}, skipping", tradingPair, desired);
            return CompletableFuture.completedFuture(null);
        }
        log.info("changing leverage for {}: {}x -> {}x", tradingPair, current, desired);
        return applyLeverage(tradingPair, desired)
                .thenRun(() -> eventPublisher.publish(new LeverageChangedEvent(tradingPair, desired)));
    }

    @Override
    public CompletableFuture<Void> ensureMarginMode(String tradingPair, MarginMode desired) {
        if (desired == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("desired margin mode cannot be null"));
        }
        Optional<MarginMode> current = tracker.findMarginMode(tradingPair);
        if (current.isPresent() && current.get() == desired) {
            log.debug("margin mode for {} already {}, skipping", tradingPair, desired);
            return CompletableFuture.completedFuture(null);
        }
        log.info("changing margin mode for {}: {} -> {}", tradingPair, current.orElse(null), desired);
        return applyMarginMode(tradingPair, desired)
                .thenRun(() -> eventPublisher.publish(new MarginModeChangedEvent(tradingPair, desired)));
    }

    protected abstract CompletableFuture<Void> applyPositionMode(PositionMode mode);
    protected abstract CompletableFuture<Void> applyLeverage(String tradingPair, int desired);
    protected abstract CompletableFuture<Void> applyMarginMode(String tradingPair, MarginMode mode);

}
