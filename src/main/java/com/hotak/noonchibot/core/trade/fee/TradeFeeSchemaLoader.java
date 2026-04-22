package com.hotak.noonchibot.core.trade.fee;

import java.util.concurrent.CompletableFuture;

public interface TradeFeeSchemaLoader {
    CompletableFuture<TradeFeeSchema> get(String tradingPair);
}
