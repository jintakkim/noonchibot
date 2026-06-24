package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.derivative.DerivativeAccountConfigurer;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderExecutor;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.Getter;

@Getter
public class DerivativeExchangeConnector extends ExchangeConnector {
    private final FundingInfoTracker fundingInfoTracker;
    private final DerivativeAccountConfigurer derivativeAccountConfigurer;

    public DerivativeExchangeConnector(
            String platformName,
            OrderTracker orderTracker,
            OrderBookTracker orderBookTracker,
            AccountBalanceTracker accountBalanceTracker,
            TradeFeeSchemaLoader tradeFeeSchemaLoader,
            TradingRuleRegistry tradingRuleRegistry,
            OrderExecutor orderExecutor,
            ExchangeLifeCycleRegistry lifeCycleRegistry,
            FundingInfoTracker fundingInfoTracker,
            DerivativeAccountConfigurer derivativeAccountConfigurer
    ) {
        super(platformName, orderTracker, orderBookTracker, accountBalanceTracker, tradeFeeSchemaLoader, tradingRuleRegistry, orderExecutor, lifeCycleRegistry);
        this.fundingInfoTracker = fundingInfoTracker;
        this.derivativeAccountConfigurer = derivativeAccountConfigurer;
    }
}
