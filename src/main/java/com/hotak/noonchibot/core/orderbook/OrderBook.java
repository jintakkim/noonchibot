package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.util.List;

public interface OrderBook {
    List<OrderBookEntry> getBidEntries();
    List<OrderBookEntry> getAskEntries();
    Long getSnapshotId();
    Long getLastDiffId();
    BigDecimal getBestBid();
    BigDecimal getBestAsk();
    BigDecimal getLastTradePrice();


}
