package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureReconciler;
import com.hotak.noonchibot.core.strategy.model.ExchangeOrderView;
import com.hotak.noonchibot.core.strategy.model.ExchangePosition;
import com.hotak.noonchibot.core.strategy.api.Strategy;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyDecision;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.model.TargetPosition;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyEngineTest {
    @Test
    @DisplayName("거래소별 target을 거래소별 주문 명령으로 변환한다")
    void onTick_convertsTargetsToExecutionPlan() {
        Strategy strategy = new Strategy() {
            @Override
            public String id() {
                return "cross-exchange-arb";
            }

            @Override
            public StrategyDecision onTick(StrategyContext context) {
                return new StrategyDecision.Targets(List.of(
                        new TargetPosition(
                                id(),
                                Exchange.BINANCE_DERIVATIVE,
                                "BTC-USDT",
                                PositionSide.LONG,
                                new BigDecimal("0.2"),
                                TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                                context.now().plusSeconds(10),
                                "long leg"
                        ),
                        new TargetPosition(
                                id(),
                                Exchange.HYPERLIQUID_DERIVATIVE,
                                "BTC-USDC",
                                PositionSide.SHORT,
                                new BigDecimal("-0.2"),
                                TargetOrderStyle.market(),
                                context.now().plusSeconds(10),
                                "short leg"
                        )
                ));
            }
        };
        StrategyEngine engine = new StrategyEngine(
                List.of(strategy),
                new ExposureCalculator(),
                new ExposureReconciler(ReconcilePolicy.conservative())
        );

        ExecutionPlan plan = engine.onTick(context(List.of(), List.of()));

        assertThat(plan.commands()).hasSize(2);
        ExecutionCommand.SubmitOrder longCommand = (ExecutionCommand.SubmitOrder) plan.commands().get(0);
        assertThat(longCommand.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
        assertThat(longCommand.candidate().getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(longCommand.candidate().getAmount()).isEqualByComparingTo("0.2");

        ExecutionCommand.SubmitOrder shortCommand = (ExecutionCommand.SubmitOrder) plan.commands().get(1);
        assertThat(shortCommand.exchange()).isEqualTo(Exchange.HYPERLIQUID_DERIVATIVE);
        assertThat(shortCommand.candidate().getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(shortCommand.candidate().getOrderType()).isEqualTo(OrderType.MARKET);
        assertThat(shortCommand.candidate().getAmount()).isEqualByComparingTo("0.2");
    }

    static StrategyContext context(
            List<ExchangePosition> positions,
            List<ExchangeOrderView> orders
    ) {
        return new StrategyContext(
                Instant.parse("2026-06-01T00:00:00Z"),
                new StrategyMarketView() {},
                new StrategyAccountView() {},
                () -> orders,
                () -> positions,
                StrategySnapshotSink.NOOP
        );
    }
}
