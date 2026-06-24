package com.hotak.noonchibot.strategy;

import java.math.BigDecimal;

/**
 * 기초 자산을 스테이블 코인으로 보고 계산을 진행한다.
 * SPOT 마켓의 경우에는 지정된 스테이블 코인을 제외한 모든 코인을 포지션으로 취급한다.
 * DETRIVATIVE 마켓의 경우에는 활성화된 포지션을 그대로 포지션으로 취급한다.
 */
public interface StableCoinBasedBalanceTracker {
    String getStableCoinName();
    BigDecimal getPosition(String coinName);


}
