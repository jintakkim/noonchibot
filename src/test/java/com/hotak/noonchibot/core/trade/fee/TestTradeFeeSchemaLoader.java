package com.hotak.noonchibot.core.trade.fee;

public class TestTradeFeeSchemaLoader implements TradeFeeSchemaLoader {
    private TradeFeeSchema tradeFeeSchema;

    public TestTradeFeeSchemaLoader(TradeFeeSchema tradeFeeSchema) {
        this.tradeFeeSchema = tradeFeeSchema;
    }

    public void setTradeFeeSchema(TradeFeeSchema tradeFeeSchema) {
        this.tradeFeeSchema = tradeFeeSchema;
    }

    @Override
    public TradeFeeSchema get(String tradingPair) {
        return tradeFeeSchema;
    }
}