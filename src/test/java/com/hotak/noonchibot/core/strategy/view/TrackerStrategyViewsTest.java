package com.hotak.noonchibot.core.strategy.view;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.Position;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.OrderView;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackerStrategyViewsTest {
    @Test
    void orderAndPositionViews_attachExchangeToTrackerData() {
        OrderTracker orderTracker = mock(OrderTracker.class);
        InFlightOrder inFlightOrder = mock(InFlightOrder.class);
        OrderView order = mock(OrderView.class);
        when(order.state()).thenReturn(OrderState.OPEN);
        when(inFlightOrder.toView()).thenReturn(order);
        when(orderTracker.getAllInFlightOrders()).thenReturn(List.of(inFlightOrder));

        PositionTracker positionTracker = mock(PositionTracker.class);
        Position position = new Position(
                "BTC-USDT",
                PositionSide.LONG,
                Instant.parse("2026-06-01T00:00:00Z"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                new BigDecimal("0.1")
        );
        when(positionTracker.getPositions()).thenReturn(List.of(position));

        var orders = new TrackerStrategyOrderView(Map.of(
                Exchange.BINANCE_DERIVATIVE,
                orderTracker
        )).openOrders();
        var positions = new TrackerStrategyPositionView(Map.of(
                Exchange.BINANCE_DERIVATIVE,
                positionTracker
        )).positions();

        assertThat(orders).singleElement().satisfies(value -> {
            assertThat(value.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
            assertThat(value.order()).isSameAs(order);
        });
        assertThat(positions).singleElement().satisfies(value -> {
            assertThat(value.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
            assertThat(value.position()).isSameAs(position);
        });
    }

    @Test
    void accountView_readsExchangeBalanceTracker() {
        AccountBalanceTracker tracker = mock(AccountBalanceTracker.class);
        when(tracker.isInitialized()).thenReturn(true);
        when(tracker.getAvailableBalance("USDT")).thenReturn(new BigDecimal("1000"));
        TrackerStrategyAccountView view = new TrackerStrategyAccountView(Map.of(
                Exchange.BINANCE_DERIVATIVE,
                tracker
        ));

        assertThat(view.availableBalance(Exchange.BINANCE_DERIVATIVE, "USDT"))
                .contains(new BigDecimal("1000"));
        assertThat(view.availableBalance(Exchange.HYPERLIQUID_DERIVATIVE, "USDT"))
                .isEmpty();
    }

    @Test
    void marketView_delegatesPriceAndFundingQueriesToExchangeTrackers() {
        OrderBookTracker orderBookTracker = mock(OrderBookTracker.class);
        FundingInfoTracker fundingInfoTracker = mock(FundingInfoTracker.class);
        when(orderBookTracker.getExecutablePrice("BTC-USDT", true, new BigDecimal("0.1")))
                .thenReturn(new BigDecimal("50001"));
        when(fundingInfoTracker.getAverageFundingRate("BTC-USDT", Duration.ofDays(7)))
                .thenReturn(new BigDecimal("0.0001"));
        TrackerStrategyMarketView view = new TrackerStrategyMarketView(
                Map.of(Exchange.BINANCE_DERIVATIVE, orderBookTracker),
                Map.of(Exchange.BINANCE_DERIVATIVE, fundingInfoTracker)
        );

        assertThat(view.executablePrice(
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                TradeType.BUY,
                new BigDecimal("0.1")
        )).isEqualByComparingTo("50001");
        assertThat(view.averageFundingRate(
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                Duration.ofDays(7)
        )).isEqualByComparingTo("0.0001");
    }
}
