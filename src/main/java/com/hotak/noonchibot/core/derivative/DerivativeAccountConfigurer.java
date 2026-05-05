package com.hotak.noonchibot.core.derivative;

import java.util.concurrent.CompletableFuture;

public interface DerivativeAccountConfigurer {
    /**
     * 포지션 모드를 원하는 값으로 맞춘다. 이미 같으면 skip.
     */
    CompletableFuture<Void> ensurePositionMode(PositionMode desired);

    /**
     * 특정 페어의 레버리지를 원하는 값으로 맞춘다. 이미 같으면 skip.
     * 담보가 부족하거나 허용하지 않는 레버리지라면 failed 될 수 있다.
     */
    CompletableFuture<Void> ensureLeverage(String tradingPair, int desired);

    /**
     * 특정 페어의 마진 모드를 원하는 값으로 맞춘다. 이미 같으면 skip.
     * 주의: 해당 페어에 open position이나 order가 있으면 거래소가 변경을 거부할 수 있다.
     */
    CompletableFuture<Void> ensureMarginMode(String tradingPair, MarginMode desired);
}
