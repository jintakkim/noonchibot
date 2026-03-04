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
        String symbol = tradingPairSymbolMap.get(tradingPair);
        if (symbol == null) {
            throw new NotRegisteredException("No exchange symbol registered for trading pair: " + tradingPair);
        }
        return symbol;
    }

    @Override
    public String convertExchangeSymbolToTradingPair(String exchangeSymbol) {
        String tradingPair = symbolTradingPairMap.get(exchangeSymbol);
        if (tradingPair == null) {
            throw new NotRegisteredException("No trading pair registered for exchange symbol: " + exchangeSymbol);
        }
        return tradingPair;
    }

    @Override
    public boolean isEmpty() {
        return symbolTradingPairMap.isEmpty() && tradingPairSymbolMap.isEmpty();
    }

    @Override
    public List<String> getAllTradingPairs() {
        return new ArrayList<>(symbolTradingPairMap.values());
    }

    @Override
    public List<String> getAllExchangeSymbols() {
        return new ArrayList<>(tradingPairSymbolMap.values());
    }
}