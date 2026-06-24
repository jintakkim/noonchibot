package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.trade.TradingRule;

@FunctionalInterface
public interface TradingRuleRegistry {
    TradingRule getTradingRule(String tradingPair);
}
