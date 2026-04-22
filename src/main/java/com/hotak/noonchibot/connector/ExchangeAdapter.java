package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.execute.OrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;

@Getter
@RequiredArgsConstructor
public class ExchangeAdapter implements SmartLifecycle {
    private final String platformName;
    private final OrderTracker orderTracker;
    private final OrderBookTracker orderBookTracker;
    private final AccountBalanceTracker accountBalanceTracker;
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;
    private final TradingRuleRegistry tradingRuleRegistry;
    private final OrderExecutor orderExecutor;
    @Getter(AccessLevel.NONE)
    private final ExchangeLifeCycleRegistry lifeCycleRegistry;
    @Getter(AccessLevel.NONE)
    private volatile boolean isRunning = false;

    @Override
    public void start() {
        isRunning = true;
        lifeCycleRegistry.startAll();
    }

    @Override
    public void stop() {
        lifeCycleRegistry.stopAll();
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}

