package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.derivative.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record LiquidationEvent(
        Instant timestamp,
        String tradingPair,
        PositionSide liquidatedSide,
        BigDecimal liquidatedSize,           // 청산된 포지션 크기
        BigDecimal liquidatedNotional,       // 명목가
        BigDecimal accountValueAtLiquidation,
        String liquidationId                 // 거래소 lid
) {}