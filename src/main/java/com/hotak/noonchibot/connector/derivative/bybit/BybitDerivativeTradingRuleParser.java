package com.hotak.noonchibot.connector.derivative.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.order.OrderType;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.*;

@RequiredArgsConstructor
public class BybitDerivativeTradingRuleParser implements TradingRuleParser {
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    @Override
    public List<TradingRule> parse(JsonNode body) {
        List<TradingRule> rules = new ArrayList<>();

        JsonNode list = body.path("result").path("list");
        if (!list.isArray()) return rules;

        for (JsonNode symbol : list) {
            if (!"Trading".equals(symbol.path("status").asString())) continue;

            String exchangeSymbol = symbol.get("symbol").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(exchangeSymbol, false);
            if (tradingPair == null) continue; // symbolRegistry에 없으면 무시

            JsonNode priceFilter = symbol.get("priceFilter");
            JsonNode lotSizeFilter = symbol.get("lotSizeFilter");

            String settleCoin = symbol.get("settleCoin").asString();

            rules.add(new TradingRule(
                    tradingPair,
                    lotSizeFilter.get("minOrderQty").asDecimal(),
                    lotSizeFilter.get("maxOrderQty").asDecimal(),
                    priceFilter.get("tickSize").asDecimal(),
                    lotSizeFilter.get("qtyStep").asDecimal(),
                    lotSizeFilter.get("minNotionalValue").asDecimal(),
                    symbol.get("priceScale").asInt(),
                    Set.of(OrderType.LIMIT, OrderType.MARKET),
                    settleCoin, // buyOrderCollateralToken
                    settleCoin     // sellOrderCollateralToken
            ));
        }
        return rules;
    }
}
