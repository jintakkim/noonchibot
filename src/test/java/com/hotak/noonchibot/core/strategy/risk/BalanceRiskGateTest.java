package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceRiskGateTest {
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void approve_rejectsAllOrdersWhenAggregatedBalanceIsInsufficient() {
        List<StrategySnapshot> snapshots = new ArrayList<>();
        ExecutionCommand.CancelOrder cancellation = new ExecutionCommand.CancelOrder(
                "arb",
                Exchange.BINANCE_DERIVATIVE,
                "cancel",
                "cid-1"
        );
        ExecutionPlan plan = new ExecutionPlan(List.of(
                submit("0.1", false),
                submit("0.1", false),
                cancellation
        ));

        ExecutionPlan approved = new BalanceRiskGate().approve(
                plan,
                context(Map.of("BINANCE_DERIVATIVE:USDT", new BigDecimal("9999")), snapshots)
        );

        assertThat(approved.commands()).containsExactly(cancellation);
        assertThat(snapshots).hasSize(1);
    }

    @Test
    void approve_allowsReduceOnlyOrderWithoutAvailableBalance() {
        ExecutionPlan plan = new ExecutionPlan(List.of(submit("1", true)));

        ExecutionPlan approved = new BalanceRiskGate().approve(plan, context(Map.of(), new ArrayList<>()));

        assertThat(approved).isEqualTo(plan);
    }

    private ExecutionCommand.SubmitOrder submit(String amount, boolean reduceOnly) {
        return new ExecutionCommand.SubmitOrder(
                "arb",
                "btc-pair",
                Exchange.BINANCE_DERIVATIVE,
                "test",
                OrderCandidate.builder()
                        .tradingPair("BTC-USDT")
                        .orderType(OrderType.LIMIT)
                        .tradeType(TradeType.BUY)
                        .amount(new BigDecimal(amount))
                        .price(new BigDecimal("50000"))
                        .timeInForce(TimeInForce.GTC)
                        .reduceOnly(reduceOnly)
                        .build()
        );
    }

    private StrategyContext context(
            Map<String, BigDecimal> balances,
            List<StrategySnapshot> snapshots
    ) {
        return new StrategyContext(
                NOW,
                StrategyMarketView.UNAVAILABLE,
                new StrategyAccountView() {
                    @Override
                    public Optional<BigDecimal> availableBalance(Exchange exchange, String asset) {
                        return Optional.ofNullable(balances.get(exchange.getId() + ":" + asset));
                    }
                },
                () -> List.of(),
                () -> List.of(),
                snapshots::add
        );
    }
}
