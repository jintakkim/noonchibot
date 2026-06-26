package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.Getter;

@Getter
public class DerivativeExchangeConnector extends ExchangeConnector {
    private final FundingInfoTracker fundingInfoTracker;
    private final PositionTracker positionTracker;

    public DerivativeExchangeConnector(
            String platformName,
            OrderTracker orderTracker,
            OrderBookTracker orderBookTracker,
            AccountBalanceTracker accountBalanceTracker,
            TradeFeeSchemaLoader tradeFeeSchemaLoader,
            TradingRuleRegistry tradingRuleRegistry,
            ExchangeOrderExecutor orderExecutor,
            FundingInfoTracker fundingInfoTracker,
            PositionTracker positionTracker
    ) {
        super(platformName,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                tradeFeeSchemaLoader,
                tradingRuleRegistry,
                orderExecutor
        );
        this.fundingInfoTracker = fundingInfoTracker;
        this.positionTracker = positionTracker;
    }
}
