package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import jakarta.annotation.PostConstruct;

import java.util.Map;

public class BinanceTradingPairSymbolRegistry extends SimpleTradingPairSymbolRegistry {
    public BinanceTradingPairSymbolRegistry(RestAssistant restAssistant) {
        super(symbolTradingPairMap, tradingPairSymbolMap);

    }

    @PostConstruct
    public void initializeUsingRestApi() {

    }
}
