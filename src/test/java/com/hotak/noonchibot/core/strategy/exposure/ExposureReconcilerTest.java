package com.hotak.noonchibot.core.strategy.exposure;

import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.OrderCandidate;
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

class ExposureReconcilerTest {
    private final ExposureReconciler reconciler = new ExposureReconciler(ReconcilePolicy.conservative());
    private final Instant now = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    @DisplayName("projected exposure가 target과 같으면 아무 명령도 만들지 않는다")
    void reconcile_whenProjectedExposureMatchesTarget_returnsEmptyPlan() {
        ExecutionPlan plan = reconciler.reconcile(
                target("1.0"),
                exposure("0.4", "0.6", "0"),
                List.of(openOrder("buy-1", TradeType.BUY, "0.6")),
                now
        );

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("target과 어긋났고 오픈 주문이 있으면 먼저 취소 명령만 만든다")
    void reconcile_whenTargetChangedAndOpenOrderExists_cancelsBeforeSubmitting() {
        ExecutionPlan plan = reconciler.reconcile(
                target("0"),
                exposure("0.4", "0.6", "0"),
                List.of(openOrder("buy-1", TradeType.BUY, "0.6")),
                now
        );

        assertThat(plan.commands()).containsExactly(
                new ExecutionCommand.CancelOrder(
                        "arb",
                        "projected exposure differs from target",
                        "buy-1"
                )
        );
    }

    @Test
    @DisplayName("오픈 주문이 없고 target보다 exposure가 작으면 매수 주문을 만든다")
    void reconcile_whenNeedsMoreExposure_submitsBuyOrder() {
        ExecutionPlan plan = reconciler.reconcile(
                target("1.0"),
                exposure("0.4", "0", "0"),
                List.of(),
                now
        );

        assertThat(plan.commands()).hasSize(1);
        ExecutionCommand.SubmitOrder command = (ExecutionCommand.SubmitOrder) plan.commands().getFirst();
        OrderCandidate candidate = command.candidate();
        assertThat(candidate.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(candidate.getAmount()).isEqualByComparingTo("0.6");
        assertThat(candidate.getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(candidate.getPrice()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("오픈 주문이 없고 target보다 exposure가 크면 매도 주문을 만든다")
    void reconcile_whenNeedsLessExposure_submitsSellOrder() {
        ExecutionPlan plan = reconciler.reconcile(
                target("0"),
                exposure("0.4", "0", "0"),
                List.of(),
                now
        );

        ExecutionCommand.SubmitOrder command = (ExecutionCommand.SubmitOrder) plan.commands().getFirst();
        assertThat(command.candidate().getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(command.candidate().getAmount()).isEqualByComparingTo("0.4");
    }

    @Test
    @DisplayName("target이 만료되면 새 주문 없이 오픈 주문을 취소한다")
    void reconcile_whenTargetExpired_cancelsOpenOrders() {
        ExecutionPlan plan = reconciler.reconcile(
                new TargetPosition(
                        "arb",
                        "BTC-USDT",
                        PositionSide.LONG,
                        BigDecimal.ONE,
                        TargetOrderStyle.market(),
                        now,
                        "expired opportunity"
                ),
                exposure("0", "1.0", "0"),
                List.of(openOrder("buy-1", TradeType.BUY, "1.0")),
                now
        );

        assertThat(plan.commands()).containsExactly(
                new ExecutionCommand.CancelOrder("arb", "target expired", "buy-1")
        );
    }

    private TargetPosition target(String amount) {
        return new TargetPosition(
                "arb",
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal(amount),
                TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                now.plusSeconds(10),
                "spread is wide enough"
        );
    }

    private ExposureSnapshot exposure(String filled, String openBuy, String openSell) {
        return new ExposureSnapshot(
                "arb",
                "BTC-USDT",
                PositionSide.LONG,
                new BigDecimal(filled),
                new BigDecimal(openBuy),
                new BigDecimal(openSell),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private OrderView openOrder(String clientOrderId, TradeType tradeType, String remaining) {
        BigDecimal remainingAmount = new BigDecimal(remaining);
        return new OrderView(
                clientOrderId,
                "ex-" + clientOrderId,
                "BTC-USDT",
                OrderType.LIMIT,
                tradeType,
                TimeInForce.GTC,
                true,
                OrderState.OPEN,
                remainingAmount,
                new BigDecimal("50000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                remainingAmount,
                Set.of(),
                Map.of(),
                now,
                now
        );
    }
}
