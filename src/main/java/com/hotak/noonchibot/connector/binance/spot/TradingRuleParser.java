package com.hotak.noonchibot.connector.binance.spot;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradingRule;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.*;

@RequiredArgsConstructor
class TradingRuleParser implements com.hotak.noonchibot.connector.TradingRuleParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public List<TradingRule> parse(JsonNode body) {
        List<TradingRule> rules = new ArrayList<>();
        JsonNode symbols = body.get("symbols");
        if (symbols == null || !symbols.isArray()) {
            return rules;
        }
        for (JsonNode symbol : symbols) {
            String exchangeSymbol = symbol.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol, false);
            if (tradingPair == null) continue;
            JsonNode filters = symbol.get("filters");
            JsonNode priceFilter = findFilter(filters, "PRICE_FILTER");
            JsonNode lotSizeFilter = findFilter(filters, "LOT_SIZE");
            JsonNode minNotionalFilter = findFilter(filters, "NOTIONAL");
            Set<OrderType> orderTypes = new HashSet<>();
            for (JsonNode orderType : symbol.get("orderTypes")) {
                try {
                    orderTypes.add(OrderType.valueOf(orderType.asString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minQty").asDecimal(),
                    lotSizeFilter.get("maxQty").asDecimal(),
                    priceFilter.get("tickSize").asDecimal(),
                    lotSizeFilter.get("stepSize").asDecimal(),
                    minNotionalFilter.get("minNotional").asDecimal(),
                    symbol.get("quotePrecision").asInt(),
                    orderTypes,
                    tradingPair.split("-")[1],
                    tradingPair.split("-")[0]
            ));
        }
        return rules;
    }

    private JsonNode findFilter(JsonNode filters, String... filterTypes) {
        Set<String> types = Set.of(filterTypes);
        for (JsonNode filter : filters) {
            if (types.contains(filter.get("filterType").asString())) {
                return filter;
            }
        }
        throw new IllegalArgumentException("Filter not found: " + Arrays.toString(filterTypes));
    }
}
