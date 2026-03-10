package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.datatype.TradingRule;

@FunctionalInterface
public interface TradingRuleRegistry {
    TradingRule getTradingRule(String tradingPair);
}
