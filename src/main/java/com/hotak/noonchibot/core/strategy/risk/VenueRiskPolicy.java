package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.util.Map;

public record VenueRiskPolicy(
        boolean rejectWholePlanOnAnySubmitRejection,
        boolean allowSubmitWhenBalanceUnknown,
        BigDecimal quoteBalanceBufferRate,
        Map<Exchange, BigDecimal> maxOrderNotionalByExchange
) {
    public VenueRiskPolicy {
        quoteBalanceBufferRate = quoteBalanceBufferRate == null ? BigDecimal.ZERO : quoteBalanceBufferRate;
        maxOrderNotionalByExchange = maxOrderNotionalByExchange == null
                ? Map.of()
                : Map.copyOf(maxOrderNotionalByExchange);
    }

    public static VenueRiskPolicy conservative() {
        return new VenueRiskPolicy(
                true,
                false,
                BigDecimal.ZERO,
                Map.of()
        );
    }
}
