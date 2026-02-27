package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.fee.TradeFee;

import java.math.BigDecimal;
import java.util.List;

public interface ExchangeConnector extends Connector {

    List<String> getAllTradingPairs();;
    TradeFee getFee(String baseCurrency, String quoteCurrency, OrderType orderType, TradeType tradeType, BigDecimal amount, BigDecimal price, boolean isMaker);
}
