package com.hotak.noonchibot.core.derivative.api;

import com.hotak.noonchibot.core.ReconcileStatus;
import com.hotak.noonchibot.core.derivative.MarginMode;
import com.hotak.noonchibot.core.derivative.PositionMode;

public interface DerivativeModeCommandApi {
    /**
     * 포지션 모드를 원하는 값으로 조정한다.
     * @return IN_SYNC인 경우에만 보장가능하다.
     */
    ReconcileStatus reconcilePositionMode(PositionMode wantTo);

    /**
     * 레버리지를 원하는 값으로 조정한다.
     * @return IN_SYNC인 경우에만 보장 가능하다.
     */
    ReconcileStatus reconcileLeverage(String tradingPair, int wantTo);

    /**
     * 마진 모드를 원하는 값으로 조정한다.
     * @return IN_SYNC인 경우에만 보장 가능하다.
     */
    ReconcileStatus reconcileMarginMode(String tradingPair, MarginMode wantTo);
}
