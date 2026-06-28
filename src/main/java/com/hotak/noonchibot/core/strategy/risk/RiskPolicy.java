package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.util.Map;

public record RiskPolicy(
        boolean rejectWholePlanOnAnySubmitRejection,
        boolean allowSubmitWhenBalanceUnknown,
        BigDecimal quoteBalanceBufferRate,
        Map<Exchange, BigDecimal> maxOrderNotionalByExchange
) {
    public RiskPolicy {
        quoteBalanceBufferRate = quoteBalanceBufferRate == null ? BigDecimal.ZERO : quoteBalanceBufferRate;
        maxOrderNotionalByExchange = maxOrderNotionalByExchange == null
                ? Map.of()
                : Map.copyOf(maxOrderNotionalByExchange);
    }

    public static RiskPolicy conservative() {
        return new RiskPolicy(
                true,
                false,
                BigDecimal.ZERO,
                Map.of()
        );
    }
}
