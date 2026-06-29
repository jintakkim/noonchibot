package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.time.Duration;

public interface StrategyMarketView {
    StrategyMarketView UNAVAILABLE = new StrategyMarketView() {
        @Override
        public BigDecimal averageFundingRate(Exchange exchange, String tradingPair, Duration window) {
            throw new IllegalStateException("funding rate market data is unavailable");
        }

        @Override
        public BigDecimal executablePrice(
                Exchange exchange,
                String tradingPair,
                TradeType tradeType,
                BigDecimal baseAmount
        ) {
            throw new IllegalStateException("executable price market data is unavailable");
        }
    };

    BigDecimal averageFundingRate(
            Exchange exchange,
            String tradingPair,
            Duration window
    );

    BigDecimal executablePrice(
            Exchange exchange,
            String tradingPair,
            TradeType tradeType,
            BigDecimal baseAmount
    );
}
