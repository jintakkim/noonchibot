package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.order.OrderType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.*;

@RequiredArgsConstructor
public class BinanceTradingRuleParser implements TradingRuleParser {
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
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol);
            if (tradingPair == null) continue;
            JsonNode filters = symbol.get("filters");
            JsonNode priceFilter = findFilter(filters, "PRICE_FILTER");
            JsonNode lotSizeFilter = findFilter(filters, "LOT_SIZE");
            JsonNode minNotionalFilter = findFilter(filters, "NOTIONAL");
            Set<OrderType> orderTypes = new HashSet<>();
            for (JsonNode ot : symbol.get("orderTypes")) {
                try {
                    orderTypes.add(OrderType.valueOf(ot.asString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minQty").asDecimal(), //min order size
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
