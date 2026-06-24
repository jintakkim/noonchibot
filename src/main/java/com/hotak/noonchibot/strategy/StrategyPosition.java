package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.core.Exchange;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class StrategyPosition {
    private final String id;
    private final Exchange exchange;
    private final String tradingPair;
    private BigDecimal size;


}
