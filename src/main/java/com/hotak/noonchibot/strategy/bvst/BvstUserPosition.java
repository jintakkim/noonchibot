package com.hotak.noonchibot.strategy.bvst;

import com.hotak.noonchibot.core.derivative.PositionSide;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class BvstUserPosition {
    @Id
    @GeneratedValue
    private Long id;

    private Long userId;
    private String cycleId;
    private String tradingPair;

    @Enumerated(EnumType.STRING)
    private PositionSide side;               // LONG / SHORT

    private BigDecimal entrySize;            // 진입 수량
    private BigDecimal entryPrice;           // 평균 진입가
    private BigDecimal entryNotional;        // size × price

    // 정산 정보
    private BigDecimal exitSize;             // 정산된 수량
    private BigDecimal exitPrice;
    private BigDecimal fee;
    private BigDecimal realizedPnl;
    private BigDecimal accumulatedFundingPnl; // 포지션에서 받은 funding 누적
}