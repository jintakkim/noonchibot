package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.SequentialDispatcher;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.Getter;

@Getter
public class DerivativeExchangeConnector extends ExchangeConnector {
    private final EventSubscriber eventSubscriber;
    private final FundingInfoTracker fundingInfoTracker;
    private final PositionTracker positionTracker;

    public DerivativeExchangeConnector(
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
            PositionTracker positionTracker
    ) {
        super(exchange,
                platformName,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                tradeFeeSchemaLoader,
                tradingRuleRegistry,
                orderExecutor,
                sequentialDispatcher,
                priceCandleDataSource
        );
        this.eventSubscriber = eventSubscriber;
        this.fundingInfoTracker = fundingInfoTracker;
        this.positionTracker = positionTracker;
    }
}
