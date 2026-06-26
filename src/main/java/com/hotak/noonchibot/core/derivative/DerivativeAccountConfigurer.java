package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.derivative.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 래버리지 설정, 포지션 모드 설정(HEDGE, ONE-WAY), 마진 모드 설정(CROSS, ISOLATED)
 */
@Slf4j
@RequiredArgsConstructor
public class DerivativeAccountConfigurer {
    private final DerivativeInfoTracker tracker;
    private final EventPublisher eventPublisher;
    
    public final EventHandler<PositionModeChangeEvent.EnsureCommand> ensurePositionModeHandler = this::ensurePositionMode;
    public final EventHandler<LeverageChangeEvent.EnsureCommand> ensureLeverageHandler = this::ensureLeverage;
    public final EventHandler<MarginModeChangeEvent.EnsureCommand> ensureMarginModeHandler = this::ensureMarginMode;

    /**
     * 포지션 모드를 원하는 값으로 맞춘다. 이미 같으면 skip.
     */
    void ensurePositionMode(PositionModeChangeEvent.EnsureCommand command) {
        Optional<PositionMode> current = tracker.findPositionMode();
        if(command.wantTo() == null) {
            eventPublisher.publish(new PositionModeChangeEvent.Failed(new IllegalArgumentException("Desired position mode cannot be null")));
            return;
        }
        if(current.isPresent() && current.get() == command.wantTo()) {
            log.debug("position mode already set to {}, skipping", command.wantTo());
            eventPublisher.publish(new PositionModeChangeEvent.Applied(current.get()));
            return;
        }
        log.debug("changing position mode: {} -> {}", current, command.wantTo());
        // 실제 거래소 요청
        eventPublisher.publish(new PositionModeChangeEvent.IORequested(command.wantTo()));
    }

    void ensureLeverage(LeverageChangeEvent.EnsureCommand command) {
        if (command.wantTo() <= 0) {
            eventPublisher.publish(new LeverageChangeEvent.Failed(new IllegalArgumentException("leverage must be positive, got: " + command.wantTo())));
            return;
        }
        Optional<Integer> current = tracker.findLeverage(command.tradingPair());
        if(current.isPresent() && current.get() == command.wantTo()) {
            log.debug("leverage for {} already {}, skipping", command.tradingPair(), command.wantTo());
            eventPublisher.publish(new LeverageChangeEvent.Applied(command.tradingPair(), command.wantTo()));
            return;
        }
        log.debug("changing leverage for {}: {}x -> {}x", command.tradingPair(), current, command.wantTo());
        eventPublisher.publish(new LeverageChangeEvent.IORequested(command.tradingPair(), command.wantTo()));
    }

    void ensureMarginMode(MarginModeChangeEvent.EnsureCommand command) {
        if(command.wantTo() == null) {
            eventPublisher.publish(new MarginModeChangeEvent.Failed(new IllegalArgumentException("Desired position mode cannot be null")));
            return;
        }
        Optional<MarginMode> current = tracker.findMarginMode(command.tradingPair());
        if(current.isPresent() && current.get() == command.wantTo()) {
            log.debug("margin mode already set to {}, skipping", command.wantTo());
            eventPublisher.publish(new MarginModeChangeEvent.Applied(command.tradingPair(), command.wantTo()));
            return;
        }
        log.debug("changing margin mode: {} -> {}", current, command.wantTo());
        // 실제 거래소 요청
        eventPublisher.publish(new MarginModeChangeEvent.IORequested(command.tradingPair(), command.wantTo()));
    }
}
