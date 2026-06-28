package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;

@Getter
@RequiredArgsConstructor
public class ExchangeConnector implements SmartLifecycle {
    private final String platformName;
    private final OrderTracker orderTracker;
    private final OrderBookTracker orderBookTracker;
    private final AccountBalanceTracker accountBalanceTracker;
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;
    private final TradingRuleRegistry tradingRuleRegistry;
    private final ExchangeOrderExecutor orderExecutor;
    @Getter(AccessLevel.NONE)
    private volatile boolean isRunning = false;

    @Override
    public void start() {
        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}
