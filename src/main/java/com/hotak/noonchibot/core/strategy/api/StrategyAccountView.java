package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.Exchange;

import java.math.BigDecimal;
import java.util.Optional;

public interface StrategyAccountView {
    default Optional<BigDecimal> availableBalance(Exchange exchange, String asset) {
        return Optional.empty();
    }
}
