package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.execute.OrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;

public class DerivativeExchangeAdapter extends ExchangeAdapter {
    public DerivativeExchangeAdapter(String platformName, OrderTracker orderTracker, OrderBookTracker orderBookTracker, AccountBalanceTracker accountBalanceTracker, TradeFeeSchemaLoader tradeFeeSchemaLoader, TradingRuleRegistry tradingRuleRegistry, OrderExecutor orderExecutor, ExchangeLifeCycleRegistry lifeCycleRegistry) {
        super(platformName, orderTracker, orderBookTracker, accountBalanceTracker, tradeFeeSchemaLoader, tradingRuleRegistry, orderExecutor, lifeCycleRegistry);
    }
}
