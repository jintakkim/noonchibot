package com.hotak.noonchibot.strategy;

import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;


public interface ExchangeAdapter {
    OrderBookTracker getOrderBookTracker();

    boolean isSpot();

    String getPlatformName();

    TradeFeeSchemaLoader getTradeFeeSchemaLoader();
}
