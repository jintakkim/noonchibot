package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;

public record VWAPForVolumeQueryResult(
        BigDecimal vwapPrice,            // 가중평균 체결가
        BigDecimal fillableBaseVolume,   // 실제 채울 수 있는 baseAsset 수량
        BigDecimal impactPrice           // 마지막 체결 호가
) {
}
