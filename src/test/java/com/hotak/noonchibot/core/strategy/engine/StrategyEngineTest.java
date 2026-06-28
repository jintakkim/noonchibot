package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureReconciler;
import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.api.Strategy;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyDecision;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.api.StrategyOrderView;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyEngineTest {
    @Test
    @DisplayName("전략이 낸 target을 노출량 기준 ExecutionPlan으로 변환한다")
    void onTick_convertsStrategyTargetToExecutionPlan() {
        Strategy strategy = new Strategy() {
            @Override
            public String id() {
                return "funding-arb";
            }

            @Override
            public StrategyDecision onTick(StrategyContext context) {
                return new StrategyDecision.Targets(List.of(new TargetPosition(
                        id(),
                        "BTC-USDT",
                        PositionSide.LONG,
                        BigDecimal.ONE,
                        TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                        context.now().plusSeconds(10),
                        "positive funding spread"
                )));
            }
        };
        StrategyEngine engine = new StrategyEngine(
                List.of(strategy),
                new ExposureCalculator(),
                new ExposureReconciler(ReconcilePolicy.conservative())
        );

        ExecutionPlan plan = engine.onTick(new StrategyContext(
                Instant.parse("2026-06-01T00:00:00Z"),
                new StrategyMarketView() {},
                new StrategyAccountView() {},
                new EmptyOrderView(),
                () -> List.of()
        ));

        assertThat(plan.commands()).hasSize(1);
        ExecutionCommand.SubmitOrder command = (ExecutionCommand.SubmitOrder) plan.commands().getFirst();
        assertThat(command.strategyId()).isEqualTo("funding-arb");
        assertThat(command.candidate().getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(command.candidate().getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(command.candidate().getAmount()).isEqualByComparingTo("1");
    }

    private static class EmptyOrderView implements StrategyOrderView {
        @Override
        public Optional<com.hotak.noonchibot.core.order.OrderView> findByClientOrderId(String clientOrderId) {
            return Optional.empty();
        }

        @Override
        public List<com.hotak.noonchibot.core.order.OrderView> openOrders() {
            return List.of();
        }
    }
}
