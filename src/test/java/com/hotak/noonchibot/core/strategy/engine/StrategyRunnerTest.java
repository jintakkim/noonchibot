package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.exposure.ReconcilePolicy;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlanExecutor;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.exposure.ExposureReconciler;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;

import com.hotak.noonchibot.core.Exchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyRunnerTest {
    @Test
    @DisplayName("tick마다 context를 만들고 strategy engine 결과를 executor로 전달한다")
    void onTick_executesEnginePlan() {
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");
        RecordingExecutor executor = new RecordingExecutor();
        ExecutionPlan expectedPlan = new ExecutionPlan(List.of(
                new ExecutionCommand.CancelOrder(
                        "funding-arb",
                        Exchange.BINANCE_DERIVATIVE,
                        "stale",
                        "cid-1"
                )
        ));
        StrategyRunner runner = new StrategyRunner(
                new StubEngine(expectedPlan),
                now -> new StrategyContext(
                        now,
                        new StrategyMarketView() {},
                        new StrategyAccountView() {},
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
        ExecutionPlan rawPlan = new ExecutionPlan(List.of(
                new ExecutionCommand.CancelOrder(
                        "funding-arb",
                        Exchange.BINANCE_DERIVATIVE,
                        "stale",
                        "cid-1"
                )
        ));
        ExecutionPlan approvedPlan = ExecutionPlan.empty();
        RecordingExecutor executor = new RecordingExecutor();
        StrategyRunner runner = new StrategyRunner(
                new StubEngine(rawPlan),
                now -> new StrategyContext(
                        now,
                        new StrategyMarketView() {},
                        new StrategyAccountView() {},
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

    private static class StubEngine extends StrategyEngine {
        private final ExecutionPlan plan;

        private StubEngine(ExecutionPlan plan) {
            super(List.of(), new ExposureCalculator(), new ExposureReconciler(ReconcilePolicy.conservative()));
            this.plan = plan;
        }

        @Override
        public ExecutionPlan onTick(StrategyContext context) {
            return plan;
        }
    }

    private static class RecordingExecutor implements ExecutionPlanExecutor {
        private final List<ExecutionPlan> executedPlans = new java.util.ArrayList<>();

        @Override
        public void execute(ExecutionPlan plan) {
            executedPlans.add(plan);
        }

        private List<ExecutionPlan> executedPlans() {
            return executedPlans;
        }
    }
}
