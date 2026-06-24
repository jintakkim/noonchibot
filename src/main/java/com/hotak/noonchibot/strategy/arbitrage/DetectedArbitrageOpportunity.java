package com.hotak.noonchibot.strategy.arbitrage;

import com.hotak.noonchibot.strategy.ExchangeAdapter;

import java.math.BigDecimal;
import java.time.Instant;

public record DetectedArbitrageOpportunity (
        String tradingPair,
        ExchangeAdapter buyGw,
        ExchangeAdapter sellGw,
        BigDecimal buyVWAPPrice,
        BigDecimal sellVWAPPrice,
        BigDecimal buyImpactPrice,
        BigDecimal sellImpactPrice,
        BigDecimal VWAPGap,
        BigDecimal fillableBaseAmount,
        BigDecimal expectedProfitWithoutFee,
        Instant timestamp

) {}
