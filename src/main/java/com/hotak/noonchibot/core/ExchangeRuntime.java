package com.hotak.noonchibot.core;

import com.hotak.noonchibot.connector.PriceCandleDataSource;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.api.OrderCommandApi;
import com.hotak.noonchibot.core.order.api.OrderQueryApi;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.event.SequentialDispatcher;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;

import java.util.List;
import java.util.Objects;

public class ExchangeRuntime {
    private final BootStrap bootStrap;
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

    public ExchangeRuntime(
            BootStrap bootStrap,
            Exchange exchange,
            String platformName,
            OrderTracker orderTracker,
            OrderBookTracker orderBookTracker,
            AccountBalanceTracker accountBalanceTracker,
            TradeFeeSchemaLoader tradeFeeSchemaLoader,
            TradingRuleRegistry tradingRuleRegistry,
            ExchangeOrderExecutor orderExecutor,
            SequentialDispatcher sequentialDispatcher,
            PriceCandleDataSource priceCandleDataSource,
            List<? extends LifecycleAware> lifecycleComponents
    ) {
        this.bootStrap = Objects.requireNonNull(bootStrap, "bootStrap");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.platformName = Objects.requireNonNull(platformName, "platformName");
        this.orderTracker = manage(orderTracker);
        this.orderBookTracker = manage(orderBookTracker);
        this.accountBalanceTracker = manage(accountBalanceTracker);
        this.tradeFeeSchemaLoader = Objects.requireNonNull(tradeFeeSchemaLoader, "tradeFeeSchemaLoader");
        this.tradingRuleRegistry = Objects.requireNonNull(tradingRuleRegistry, "tradingRuleRegistry");
        this.orderExecutor = manage(orderExecutor);
        this.sequentialDispatcher = Objects.requireNonNull(sequentialDispatcher, "sequentialDispatcher");
        this.priceCandleDataSource = Objects.requireNonNull(priceCandleDataSource, "priceCandleDataSource");
        Objects.requireNonNull(lifecycleComponents, "lifecycleComponents").forEach(this::manage);
    }

    protected final <T extends LifecycleAware> T manage(T component) {
        T managedComponent = Objects.requireNonNull(component, "managed component");
        bootStrap.register(managedComponent);
        return managedComponent;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public OrderCommandApi getOrderApi() {
        return orderExecutor;
    }

    public OrderQueryApi getOrderQueryApi() {
        return orderTracker;
    }
}
