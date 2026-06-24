package com.hotak.noonchibot.core.trade;

public interface TradeFeeSchemaLoader {
    TradeFeeSchema get(String tradingPair);
}
