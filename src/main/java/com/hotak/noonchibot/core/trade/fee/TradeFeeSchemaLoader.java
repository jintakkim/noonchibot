package com.hotak.noonchibot.core.trade.fee;

public interface TradeFeeSchemaLoader {
    TradeFeeSchema get(String tradingPair);
}
