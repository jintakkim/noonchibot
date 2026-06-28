package com.hotak.noonchibot.core.strategy.exposure;

import com.hotak.noonchibot.core.derivative.Position;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderView;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExposureCalculatorTest {
    private final ExposureCalculator calculator = new ExposureCalculator();

    @Test
    @DisplayName("포지션과 오픈 주문을 합산해 projected exposure를 계산한다")
    void calculate_includesPositionAndOpenOrders() {
        ExposureSnapshot snapshot = calculator.calculate(
                "arb",
                "BTC-USDT",
                PositionSide.LONG,
                List.of(position("BTC-USDT", PositionSide.LONG, "0.4")),
                List.of(
                        order("buy-1", TradeType.BUY, OrderState.OPEN, "0.6"),
                        order("sell-1", TradeType.SELL, OrderState.OPEN, "0.1")
                )
        );

        assertThat(snapshot.filledBaseAmount()).isEqualByComparingTo("0.4");
        assertThat(snapshot.openBuyBaseAmount()).isEqualByComparingTo("0.6");
        assertThat(snapshot.openSellBaseAmount()).isEqualByComparingTo("0.1");
        assertThat(snapshot.projectedBaseAmount()).isEqualByComparingTo("0.9");
    }

    @Test
    @DisplayName("취소 대기 주문은 open exposure와 별도로 분리한다")
    void calculate_separatesPendingCancelOrders() {
        ExposureSnapshot snapshot = calculator.calculate(
                "arb",
                "BTC-USDT",
                PositionSide.LONG,
                List.of(position("BTC-USDT", PositionSide.LONG, "0.4")),
                List.of(order("buy-1", TradeType.BUY, OrderState.PENDING_CANCEL, "0.6"))
        );

        assertThat(snapshot.openBuyBaseAmount()).isZero();
        assertThat(snapshot.pendingCancelBuyBaseAmount()).isEqualByComparingTo("0.6");
        assertThat(snapshot.projectedBaseAmount()).isEqualByComparingTo("0.4");
        assertThat(snapshot.cancelingProjectedBaseAmount()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("SHORT 포지션은 음수 exposure로 정규화한다")
    void calculate_normalizesShortPositionAsNegativeExposure() {
        ExposureSnapshot snapshot = calculator.calculate(
                "relative-strength",
                "ETH-USDT",
                PositionSide.SHORT,
                List.of(position("ETH-USDT", PositionSide.SHORT, "2.0")),
                List.of()
        );

        assertThat(snapshot.filledBaseAmount()).isEqualByComparingTo("-2.0");
        assertThat(snapshot.projectedBaseAmount()).isEqualByComparingTo("-2.0");
    }

    private Position position(String tradingPair, PositionSide side, String amount) {
        return new Position(
                tradingPair,
                side,
                Instant.parse("2026-06-01T00:00:00Z"),
                BigDecimal.ZERO,
                new BigDecimal("50000"),
                new BigDecimal(amount)
        );
    }

    private OrderView order(String clientOrderId, TradeType tradeType, OrderState state, String remaining) {
        BigDecimal remainingAmount = new BigDecimal(remaining);
        return new OrderView(
                clientOrderId,
                "ex-" + clientOrderId,
                "BTC-USDT",
                OrderType.LIMIT,
                tradeType,
                TimeInForce.GTC,
                true,
                state,
                remainingAmount,
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                remainingAmount,
                Set.of(),
                Map.of(),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z")
        );
    }
}
