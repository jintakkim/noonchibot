package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.execution.VenueExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlan;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureReconciler;
import com.hotak.noonchibot.core.strategy.model.VenueOrderView;
import com.hotak.noonchibot.core.strategy.model.VenuePosition;
import com.hotak.noonchibot.core.strategy.api.VenueStrategy;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyDecision;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyMarketView;
import com.hotak.noonchibot.core.strategy.model.VenueTargetPosition;

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

class VenueStrategyEngineTest {
    @Test
    @DisplayName("거래소별 target을 거래소별 주문 명령으로 변환한다")
    void onTick_convertsVenueTargetsToVenueExecutionPlan() {
        VenueStrategy strategy = new VenueStrategy() {
            @Override
            public String id() {
                return "cross-exchange-arb";
            }

            @Override
            public VenueStrategyDecision onTick(VenueStrategyContext context) {
                return new VenueStrategyDecision.Targets(List.of(
                        new VenueTargetPosition(
                                id(),
                                Exchange.BINANCE_DERIVATIVE,
                                "BTC-USDT",
                                PositionSide.LONG,
                                new BigDecimal("0.2"),
                                TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                                context.now().plusSeconds(10),
                                "long leg"
                        ),
                        new VenueTargetPosition(
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
        VenueStrategyEngine engine = new VenueStrategyEngine(
                List.of(strategy),
                new VenueExposureCalculator(),
                new VenueExposureReconciler(ReconcilePolicy.conservative())
        );

        VenueExecutionPlan plan = engine.onTick(context(List.of(), List.of()));

        assertThat(plan.commands()).hasSize(2);
        VenueExecutionCommand.SubmitOrder longCommand = (VenueExecutionCommand.SubmitOrder) plan.commands().get(0);
        assertThat(longCommand.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
        assertThat(longCommand.candidate().getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(longCommand.candidate().getAmount()).isEqualByComparingTo("0.2");

        VenueExecutionCommand.SubmitOrder shortCommand = (VenueExecutionCommand.SubmitOrder) plan.commands().get(1);
        assertThat(shortCommand.exchange()).isEqualTo(Exchange.HYPERLIQUID_DERIVATIVE);
        assertThat(shortCommand.candidate().getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(shortCommand.candidate().getOrderType()).isEqualTo(OrderType.MARKET);
        assertThat(shortCommand.candidate().getAmount()).isEqualByComparingTo("0.2");
    }

    static VenueStrategyContext context(
            List<VenuePosition> positions,
            List<VenueOrderView> orders
    ) {
        return new VenueStrategyContext(
                Instant.parse("2026-06-01T00:00:00Z"),
                new VenueStrategyMarketView() {},
                new VenueStrategyAccountView() {},
                () -> orders,
                () -> positions,
                StrategySnapshotSink.NOOP
        );
    }
}
