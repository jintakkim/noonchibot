package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.event.SequentialDispatcher;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ExchangeConnector {
    private final Exchange exchange;
    private final String platformName;
    private final OrderTracker orderTracker;
    private final OrderBookTracker orderBookTracker;
    private final AccountBalanceTracker accountBalanceTracker;
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;
    private final TradingRuleRegistry tradingRuleRegistry;
    private final ExchangeOrderExecutor orderExecutor;
    private final SequentialDispatcher sequentialDispatcher;
    private final PriceCandleDataSource priceCandleDataSource;
}
