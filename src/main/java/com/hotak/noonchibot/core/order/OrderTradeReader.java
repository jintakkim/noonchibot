package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.event.internal.trade.TradeEvent;

public interface OrderTradeReader {
    TradeEvent.Received fetch(String clientOrderId, String exchangeOrderId, String tradingPair);
}
