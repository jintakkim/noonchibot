package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.trade.TradingRule;
import tools.jackson.databind.JsonNode;

import java.util.List;

public interface TradingRuleParser {
    List<TradingRule> parse(JsonNode body);
}
