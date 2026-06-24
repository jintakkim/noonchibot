package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.connector.ExchangeConnector;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;


public class ExchangeAdapterImpl<C extends ExchangeConnector> implements ExchangeAdapter {
    protected final C connector;

    public ExchangeAdapterImpl(C connector) {
        this.connector = connector;
    }

    @Override
    public OrderBookTracker getOrderBookTracker() {
        return connector.getOrderBookTracker();
    }

    @Override
    public boolean isSpot() {
        return true;
    }

    @Override
    public String getPlatformName() {
        return connector.getPlatformName();
    }

    @Override
    public TradeFeeSchemaLoader getTradeFeeSchemaLoader() {
        return connector.getTradeFeeSchemaLoader();
    }
}
