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
public abstract class AbstractDerivativeAccountConfigurer {
    private final DerivativeInfoTracker tracker;
    private final ExchangeEventPublisher eventPublisher;

    /**
     * 포지션 모드를 원하는 값으로 맞춘다. 이미 같으면 skip.
     */
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

    /**
     * 특정 페어의 레버리지를 원하는 값으로 맞춘다. 이미 같으면 skip.
     * 담보가 부족하거나 허용하지 않는 레버리지라면 failed 될 수 있다.
     */
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

    /**
     * 특정 페어의 마진 모드를 원하는 값으로 맞춘다. 이미 같으면 skip.
     * 주의: 해당 페어에 open position이나 order가 있으면 거래소가 변경을 거부할 수 있다.
     */
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
