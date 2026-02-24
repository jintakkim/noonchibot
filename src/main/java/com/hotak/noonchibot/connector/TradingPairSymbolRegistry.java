package com.hotak.noonchibot.connector;

import java.util.List;

public interface TradingPairSymbolRegistry {
    /**
     * tradingPair로 exchangeSymbol을 찾는다.
     *
     * @return exchangeSymbol
     * @throws NotRegisteredException 매칭되는 exchangeSymbol이 없을때
     */
    String convertTradingPairToExchangeSymbol(String tradingPair);
    /**
     * exchangeSymbol로 tradingPair를 찾는다.
     *
     * @return tradingPair
     * @throws NotRegisteredException 매칭되는 tradingPair이 없을때
     */
    String convertExchangeSymbolToTradingPair(String exchangeSymbol);

    boolean isEmpty();

    List<String> getAllTradingPairs();
}
