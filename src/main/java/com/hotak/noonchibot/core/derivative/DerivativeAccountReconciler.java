package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.ReconcileStatus;
import com.hotak.noonchibot.core.derivative.api.DerivativeModeCommandApi;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 래버리지 설정, 포지션 모드 설정(HEDGE, ONE-WAY), 마진 모드 설정(CROSS, ISOLATED)
 */
@Slf4j
@RequiredArgsConstructor
public class DerivativeAccountReconciler implements DerivativeModeCommandApi {
    private final DerivativeAccountTracker tracker;
    private final EventPublisher eventPublisher;

    /**
     * 포지션 모드의 현재 상태를 원하는 값과 조정한다. 이미 같으면 skip.
     */
    @Override
    public ReconcileStatus reconcilePositionMode(PositionMode wantTo) {
        Optional<PositionMode> current = tracker.findPositionMode();
        if(wantTo == null) throw new IllegalArgumentException("Desired position mode cannot be null");
        if(current.isPresent() && current.get() == wantTo) {
            log.debug("position mode already set to {}, skipping", wantTo);
            return ReconcileStatus.IN_SYNC;
        }
        log.debug("changing position mode: {} -> {}", current, wantTo);
        // 실제 거래소 요청
        eventPublisher.publish(new PositionModeChangeIORequestedEvent(wantTo));
        return ReconcileStatus.APPLYING;
    }

    @Override
    public ReconcileStatus reconcileLeverage(String tradingPair, int wantTo) {
        if (wantTo <= 0) {
            throw new IllegalArgumentException("leverage must be positive, got: " + wantTo);
        }
        Optional<Integer> current = tracker.findLeverage(tradingPair);
        if (current.isPresent() && current.get() == wantTo) {
            log.debug("leverage for {} already {}, skipping", tradingPair, wantTo);
            return ReconcileStatus.IN_SYNC;
        }
        log.debug("changing leverage for {}: {}x -> {}x", tradingPair, current, wantTo);
        eventPublisher.publish(new LeverageChangeIORequestedEvent(tradingPair, wantTo));
        return ReconcileStatus.APPLYING;
    }

    @Override
    public ReconcileStatus reconcileMarginMode(String tradingPair, MarginMode wantTo) {
        if (wantTo == null) {
            throw new IllegalArgumentException("Desired margin mode cannot be null");
        }
        Optional<MarginMode> current = tracker.findMarginMode(tradingPair);
        if (current.isPresent() && current.get() == wantTo) {
            log.debug("margin mode for {} already {}, skipping", tradingPair, wantTo);
            return ReconcileStatus.IN_SYNC;
        }
        log.debug("changing margin mode for {}: {} -> {}", tradingPair, current, wantTo);
        eventPublisher.publish(new MarginModeChangeIORequestedEvent(tradingPair, wantTo));
        return ReconcileStatus.APPLYING;
    }
}
