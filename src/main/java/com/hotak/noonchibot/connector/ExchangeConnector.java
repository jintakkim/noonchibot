package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.connector.auth.Authenticator;

import java.util.List;

public interface ExchangeConnector extends Connector {

    List<String> getAllTradingPairs();
    String getExchangeSymbol(String tradingPair);
    String getTradingPair(String symbol);
}
