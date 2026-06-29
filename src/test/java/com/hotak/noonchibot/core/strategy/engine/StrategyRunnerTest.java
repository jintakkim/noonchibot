package com.hotak.noonchibot.core.strategy.engine;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlanExecutor;
import com.hotak.noonchibot.core.strategy.api.Strategy;
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
    @DisplayName("tick마다 context를 만들고 strategy plan을 executor로 전달한다")
    void onTick_executesStrategyPlan() {
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
                new StubStrategy(expectedPlan),
                now -> new StrategyContext(
                        now,
                        StrategyMarketView.UNAVAILABLE,
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
                new StubStrategy(rawPlan),
                now -> new StrategyContext(
                        now,
                        StrategyMarketView.UNAVAILABLE,
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

    @Test
    @DisplayName("risk gate가 조정한 plan을 실행한다")
    void onTick_executesRiskAdjustedPlan() {
        Instant timestamp = Instant.parse("2026-06-01T00:00:00Z");
        ExecutionPlan rawPlan = new ExecutionPlan(List.of(new ExecutionCommand.CancelOrder(
                "funding-arb",
                Exchange.BINANCE_DERIVATIVE,
                "raw",
                "cid-1"
        )));
        ExecutionPlan sizedPlan = new ExecutionPlan(List.of(new ExecutionCommand.CancelOrder(
                "funding-arb",
                Exchange.BINANCE_DERIVATIVE,
                "sized",
                "cid-1"
        )));
        RecordingExecutor executor = new RecordingExecutor();
        StrategyRunner runner = new StrategyRunner(
                new StubStrategy(rawPlan),
                now -> new StrategyContext(
                        now,
                        StrategyMarketView.UNAVAILABLE,
                        new StrategyAccountView() {},
                        () -> List.of(),
                        () -> List.of(),
                        StrategySnapshotSink.NOOP
                ),
                (plan, context) -> {
                    assertThat(plan).isSameAs(rawPlan);
                    return sizedPlan;
                },
                executor
        );

        runner.onTick(timestamp);

        assertThat(executor.executedPlans()).containsExactly(sizedPlan);
    }

    private static class StubStrategy implements Strategy {
        private final ExecutionPlan plan;

        private StubStrategy(ExecutionPlan plan) {
            this.plan = plan;
        }

        @Override
        public String id() {
            return "stub";
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
