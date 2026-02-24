package com.hotak.noonchibot.connector;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SimpleTradingPairSymbolRegistry implements TradingPairSymbolRegistry {
    private final Map<String, String> symbolTradingPairMap;
    private final Map<String, String> tradingPairSymbolMap;

    @Override
    public String convertTradingPairToExchangeSymbol(String tradingPair) {
        return tradingPairSymbolMap.get(tradingPair);
    }

    @Override
    public String convertExchangeSymbolToTradingPair(String exchangeSymbol) {
        return symbolTradingPairMap.get(exchangeSymbol);
    }

    @Override
    public boolean isEmpty() {
        return symbolTradingPairMap.isEmpty() && tradingPairSymbolMap.isEmpty();
    }

    @Override
    public List<String> getAllTradingPairs() {
        return new ArrayList<>(symbolTradingPairMap.values());
    }
}
