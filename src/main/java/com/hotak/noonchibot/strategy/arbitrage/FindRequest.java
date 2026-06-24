package com.hotak.noonchibot.strategy.arbitrage;

import java.math.BigDecimal;

public record FindRequest(
        /**
         * quote 기준 주문 수량
         * 호가창을 보고 실질적인 vwap을 기준으로 갭을 계산한다.
         */
        BigDecimal orderAmount
) {
}
