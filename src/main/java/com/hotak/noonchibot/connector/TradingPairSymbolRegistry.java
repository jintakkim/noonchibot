package com.hotak.noonchibot.connector;

import java.util.List;

public interface TradingPairSymbolRegistry {
    /**
     * tradingPair로 exchangeSymbol을 찾는다.
     *
     * @return exchangeSymbol, 매칭되는 exchangeSymbol이 없다면 null
     */
    String convertTradingPairToExchangeSymbol(String tradingPair);
    /**
     * exchangeSymbol로 tradingPair를 찾는다.
     *
     * @return tradingPair, 매칭되는 tradingPair이 없다면 null
     */
    String convertExchangeSymbolToTradingPair(String exchangeSymbol);

    boolean isEmpty();

    List<String> getAllTradingPairs();
}
