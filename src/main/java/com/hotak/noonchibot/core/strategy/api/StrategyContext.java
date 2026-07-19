package com.hotak.noonchibot.core.strategy.api;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.runtime.DerivativeExchangeApi;
import com.hotak.noonchibot.core.runtime.ExchangeApi;
import com.hotak.noonchibot.core.runtime.ExchangeApiProvider;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;

import java.time.Instant;
import java.util.Objects;

public record StrategyContext(
        Instant now,
        StrategyMarketView marketView,
        StrategyAccountView accountView,
        StrategyOrderView orderView,
        StrategyPositionView positionView,
        StrategySnapshotSink snapshotSink,
        ExchangeApiProvider exchangeApis
) {
    public StrategyContext {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(marketView, "marketView");
        Objects.requireNonNull(accountView, "accountView");
        Objects.requireNonNull(orderView, "orderView");
        Objects.requireNonNull(positionView, "positionView");
        snapshotSink = snapshotSink == null ? StrategySnapshotSink.NOOP : snapshotSink;
        exchangeApis = exchangeApis == null ? ExchangeApiProvider.UNAVAILABLE : exchangeApis;
    }

    public StrategyContext(
            Instant now,
            StrategyMarketView marketView,
            StrategyAccountView accountView,
            StrategyOrderView orderView,
            StrategyPositionView positionView,
            StrategySnapshotSink snapshotSink
    ) {
        this(
                now,
                marketView,
                accountView,
                orderView,
                positionView,
                snapshotSink,
                ExchangeApiProvider.UNAVAILABLE
        );
    }

    public ExchangeApi exchange(Exchange exchange) {
        return exchangeApis.getExchange(exchange);
    }

    public DerivativeExchangeApi derivativeExchange(Exchange exchange) {
        return exchangeApis.getDerivativeExchange(exchange);
    }
}
