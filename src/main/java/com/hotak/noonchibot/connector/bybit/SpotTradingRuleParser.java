package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.order.OrderType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.*;

@RequiredArgsConstructor
class SpotTradingRuleParser implements TradingRuleParser {
    // Bybit은 오더 타입을 제공하지 않는다
    private static final Set<OrderType> SUPPORTED_ORDER_TYPES =
            Set.of(OrderType.LIMIT, OrderType.MARKET);

    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public List<TradingRule> parse(JsonNode body) {
        List<TradingRule> rules = new ArrayList<>();
        JsonNode list = body.get("result").get("list");
        if (list == null || !list.isArray()) {
            return rules;
        }
        for (JsonNode symbol : list) {
            if (!"Trading".equals(symbol.get("status").asString())) continue;

            String exchangeSymbol = symbol.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol, false);
            if (tradingPair == null) continue;

            JsonNode lotSizeFilter = symbol.get("lotSizeFilter");
            JsonNode priceFilter = symbol.get("priceFilter");

            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minOrderQty").asDecimal(),
                    lotSizeFilter.get("maxOrderQty").asDecimal(),
                    priceFilter.get("tickSize").asDecimal(),
                    lotSizeFilter.get("basePrecision").asDecimal(),
                    lotSizeFilter.get("minOrderAmt").asDecimal(),
                    lotSizeFilter.get("quotePrecision").asDecimal().scale(),
                    SUPPORTED_ORDER_TYPES,
                    symbol.get("quoteCoin").asString(),
                    symbol.get("baseCoin").asString()
            ));
        }
        return rules;
    }
}
