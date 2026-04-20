package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.order.OrderType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.*;

@RequiredArgsConstructor
public class BybitTradingRuleParser implements TradingRuleParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public List<TradingRule> parse(JsonNode body) {
        List<TradingRule> rules = new ArrayList<>();

        JsonNode resultNode = body.get("result");
        if (resultNode == null || !resultNode.has("list")) {
            return rules;
        }

        JsonNode list = resultNode.get("list");
        if (!list.isArray()) {
            return rules;
        }

        for (JsonNode symbol : list) {
            String exchangeSymbol = symbol.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);

            if (tradingPair == null) continue;

            JsonNode lotSizeFilter = symbol.get("lotSizeFilter");
            JsonNode priceFilter = symbol.get("priceFilter");

            Set<OrderType> orderTypes = new HashSet<>(Arrays.asList(OrderType.LIMIT, OrderType.MARKET));
            int quotePrecision = symbol.has("priceScale") ? symbol.get("priceScale").asInt() : 8;

            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minOrderQty").asDecimal(),
                    lotSizeFilter.get("maxOrderQty").asDecimal(),
                    priceFilter.get("tickSize").asDecimal(),
                    lotSizeFilter.get("qtyStep").asDecimal(),
                    lotSizeFilter.get("minNotionalValue").asDecimal(),
                    quotePrecision,
                    orderTypes,
                    tradingPair.split("-")[1],
                    tradingPair.split("-")[0]
            ));
        }
        return rules;
    }
}
