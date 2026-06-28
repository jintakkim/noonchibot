package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.strategy.execution.VenueExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.VenueExecutionPlanExecutor;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureReconciler;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyMarketView;

import com.hotak.noonchibot.core.Exchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VenueStrategyRunnerTest {
    @Test
    @DisplayName("tick마다 context를 만들고 strategy engine 결과를 executor로 전달한다")
    void onTick_executesEnginePlan() {
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");
        RecordingExecutor executor = new RecordingExecutor();
        VenueExecutionPlan expectedPlan = new VenueExecutionPlan(List.of(
                new VenueExecutionCommand.CancelOrder(
                        "funding-arb",
                        Exchange.BINANCE_DERIVATIVE,
                        "stale",
                        "cid-1"
                )
        ));
        VenueStrategyRunner runner = new VenueStrategyRunner(
                new StubEngine(expectedPlan),
                now -> new VenueStrategyContext(
                        now,
                        new VenueStrategyMarketView() {},
                        new VenueStrategyAccountView() {},
                        () -> List.of(),
                        () -> List.of(),
                        StrategySnapshotSink.NOOP
                ),
                executor
        );

        runner.onTick(timestamp);

        assertThat(executor.executedPlans()).containsExactly(expectedPlan);
        assertThat(runner.getCurrentTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("tick 실행 시 risk gate가 승인한 plan만 executor로 전달한다")
    void onTick_executesOnlyRiskApprovedPlan() {
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");
        VenueExecutionPlan rawPlan = new VenueExecutionPlan(List.of(
                new VenueExecutionCommand.CancelOrder(
                        "funding-arb",
                        Exchange.BINANCE_DERIVATIVE,
                        "stale",
                        "cid-1"
                )
        ));
        VenueExecutionPlan approvedPlan = VenueExecutionPlan.empty();
        RecordingExecutor executor = new RecordingExecutor();
        VenueStrategyRunner runner = new VenueStrategyRunner(
                new StubEngine(rawPlan),
                now -> new VenueStrategyContext(
                        now,
                        new VenueStrategyMarketView() {},
                        new VenueStrategyAccountView() {},
                        () -> List.of(),
                        () -> List.of(),
                        StrategySnapshotSink.NOOP
                ),
                (plan, context) -> approvedPlan,
                executor
        );

        runner.onTick(timestamp);

        assertThat(executor.executedPlans()).isEmpty();
    }

    private static class StubEngine extends VenueStrategyEngine {
        private final VenueExecutionPlan plan;

        private StubEngine(VenueExecutionPlan plan) {
            super(List.of(), new VenueExposureCalculator(), new VenueExposureReconciler(ReconcilePolicy.conservative()));
            this.plan = plan;
        }

        @Override
        public VenueExecutionPlan onTick(VenueStrategyContext context) {
            return plan;
        }
    }

    private static class RecordingExecutor implements VenueExecutionPlanExecutor {
        private final List<VenueExecutionPlan> executedPlans = new java.util.ArrayList<>();

        @Override
        public void execute(VenueExecutionPlan plan) {
            executedPlans.add(plan);
        }

        private List<VenueExecutionPlan> executedPlans() {
            return executedPlans;
        }
    }
}
