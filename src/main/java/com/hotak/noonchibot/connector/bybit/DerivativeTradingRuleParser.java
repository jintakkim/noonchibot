package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.core.trade.TradingRule;
import com.hotak.noonchibot.core.order.OrderType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
class DerivativeTradingRuleParser implements TradingRuleParser {
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
                    lotSizeFilter.get("qtyStep").asDecimal(),
                    lotSizeFilter.get("minNotionalValue").asDecimal(),
                    symbol.get("priceScale").asInt(),
                    SUPPORTED_ORDER_TYPES,
                    symbol.get("quoteCoin").asString(),
                    symbol.get("baseCoin").asString()
            ));
        }
        return rules;
    }
}