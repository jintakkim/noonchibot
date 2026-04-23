package com.hotak.noonchibot.core.trade.fee;

import java.util.concurrent.CompletableFuture;

public class TestTradeFeeSchemaLoader implements TradeFeeSchemaLoader {
    private TradeFeeSchema tradeFeeSchema;

    public TestTradeFeeSchemaLoader(TradeFeeSchema tradeFeeSchema) {
        this.tradeFeeSchema = tradeFeeSchema;
    }

    public void setTradeFeeSchema(TradeFeeSchema tradeFeeSchema) {
        this.tradeFeeSchema = tradeFeeSchema;
    }

    @Override
    public CompletableFuture<TradeFeeSchema> get(String tradingPair) {
        return CompletableFuture.completedFuture(tradeFeeSchema);
    }
}