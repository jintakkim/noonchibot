package com.hotak.noonchibot.core;

import com.hotak.noonchibot.connector.PriceCandleDataSource;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.derivative.DerivativeAccountTracker;
import com.hotak.noonchibot.core.derivative.api.DerivativeModeCommandApi;
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.SequentialDispatcher;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
public class DerivativeExchangeRuntime extends ExchangeRuntime {
    private final EventSubscriber eventSubscriber;
    private final FundingInfoTracker fundingInfoTracker;
    private final PositionTracker positionTracker;
    private final DerivativeAccountTracker derivativeAccountTracker;
    private final Optional<DerivativeModeCommandApi> derivativeModeCommandApi;

    public DerivativeExchangeRuntime(
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
            EventSubscriber eventSubscriber,
            PriceCandleDataSource priceCandleDataSource,
            FundingInfoTracker fundingInfoTracker,
            PositionTracker positionTracker,
            DerivativeAccountTracker derivativeAccountTracker,
            Optional<DerivativeModeCommandApi> derivativeModeCommandApi,
            List<? extends LifecycleAware> lifecycleComponents

    ) {
        super(bootStrap,
                exchange,
                platformName,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                tradeFeeSchemaLoader,
                tradingRuleRegistry,
                orderExecutor,
                sequentialDispatcher,
                priceCandleDataSource,
                lifecycleComponents
        );
        this.eventSubscriber = Objects.requireNonNull(eventSubscriber, "eventSubscriber");
        this.fundingInfoTracker = manage(fundingInfoTracker);
        this.positionTracker = manage(positionTracker);
        this.derivativeAccountTracker = manage(derivativeAccountTracker);
        this.derivativeModeCommandApi = Objects.requireNonNull(
                derivativeModeCommandApi,
                "derivativeModeCommandApi"
        );
    }

    public Optional<DerivativeModeCommandApi> getDerivativeModeCommandApi() {
        return derivativeModeCommandApi;
    }
}
