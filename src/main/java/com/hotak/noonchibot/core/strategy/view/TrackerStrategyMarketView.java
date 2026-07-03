package com.hotak.noonchibot.core.strategy.view;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class TrackerStrategyMarketView implements StrategyMarketView {
    private final Map<Exchange, OrderBookTracker> orderBookTrackers;
    private final Map<Exchange, FundingInfoTracker> fundingInfoTrackers;

    public TrackerStrategyMarketView(
            Map<Exchange, OrderBookTracker> orderBookTrackers,
            Map<Exchange, FundingInfoTracker> fundingInfoTrackers
    ) {
        Objects.requireNonNull(orderBookTrackers, "orderBookTrackers");
        Objects.requireNonNull(fundingInfoTrackers, "fundingInfoTrackers");
        this.orderBookTrackers = Map.copyOf(orderBookTrackers);
        this.fundingInfoTrackers = Map.copyOf(fundingInfoTrackers);
    }

    @Override
    public BigDecimal averageFundingRate(Exchange exchange, String tradingPair, Duration window) {
        FundingInfoTracker tracker = fundingInfoTrackers.get(exchange);
        if (tracker == null) {
            throw new IllegalStateException("funding tracker not found: " + exchange);
        }
        return tracker.getAverageFundingRate(tradingPair, window);
    }

    @Override
    public BigDecimal executablePrice(
            Exchange exchange,
            String tradingPair,
            TradeType tradeType,
            BigDecimal baseAmount
    ) {
        OrderBookTracker tracker = orderBookTrackers.get(exchange);
        if (tracker == null) {
            throw new IllegalStateException("order book tracker not found: " + exchange);
        }
        return tracker.getExecutablePrice(
                tradingPair,
                tradeType == TradeType.BUY,
                baseAmount
        );
    }
}
