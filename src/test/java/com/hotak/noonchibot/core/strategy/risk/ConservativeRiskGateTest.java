package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConservativeRiskGateTest {
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    @DisplayName("잔고 부족으로 submit 하나가 거절되면 전체 submit plan을 막고 cancel만 통과시킨다")
    void approve_whenAnySubmitRejected_rejectsAllSubmitsButKeepsCancels() {
        List<StrategySnapshot> snapshots = new ArrayList<>();
        ConservativeRiskGate riskGate = new ConservativeRiskGate(RiskPolicy.conservative());
        ExecutionCommand.SubmitOrder buy = submit(
                Exchange.BINANCE_DERIVATIVE,
                TradeType.BUY,
                "BTC-USDT",
                "0.2",
                "50000"
        );
        ExecutionCommand.SubmitOrder sell = submit(
                Exchange.HYPERLIQUID_DERIVATIVE,
                TradeType.SELL,
                "BTC-USDC",
                "0.2",
                "50000"
        );
        ExecutionCommand.CancelOrder cancel = new ExecutionCommand.CancelOrder(
                "funding-arb",
                Exchange.BINANCE_DERIVATIVE,
                "stale",
                "cid-1"
        );

        ExecutionPlan approved = riskGate.approve(
                new ExecutionPlan(List.of(buy, sell, cancel)),
                context(Map.of(
                        "BINANCE_DERIVATIVE:USDT", new BigDecimal("9999.99"),
                        "HYPERLIQUID_DERIVATIVE:USDC", new BigDecimal("100000")
                ), snapshots)
        );

        assertThat(approved.commands()).containsExactly(cancel);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().type()).isEqualTo("RISK_REJECTION");
        assertThat(snapshots.getFirst().metrics().get("reasons").toString())
                .contains("insufficient balance");
    }

    @Test
    @DisplayName("잔고가 충분하면 submit plan을 그대로 통과시킨다")
    void approve_whenBalanceIsEnough_passesPlan() {
        ConservativeRiskGate riskGate = new ConservativeRiskGate(RiskPolicy.conservative());
        ExecutionPlan plan = new ExecutionPlan(List.of(submit(
                Exchange.BINANCE_DERIVATIVE,
                TradeType.BUY,
                "BTC-USDT",
                "0.2",
                "50000"
        )));

        ExecutionPlan approved = riskGate.approve(
                plan,
                context(Map.of("BINANCE_DERIVATIVE:USDT", new BigDecimal("10000")), new ArrayList<>())
        );

        assertThat(approved).isEqualTo(plan);
    }

    @Test
    @DisplayName("거래소별 최대 주문 notional을 넘으면 submit을 거절한다")
    void approve_whenOrderNotionalExceedsLimit_rejectsSubmit() {
        ConservativeRiskGate riskGate = new ConservativeRiskGate(new RiskPolicy(
                true,
                false,
                BigDecimal.ZERO,
                Map.of(Exchange.BINANCE_DERIVATIVE, new BigDecimal("9999"))
        ));
        List<StrategySnapshot> snapshots = new ArrayList<>();

        ExecutionPlan approved = riskGate.approve(
                new ExecutionPlan(List.of(submit(
                        Exchange.BINANCE_DERIVATIVE,
                        TradeType.BUY,
                        "BTC-USDT",
                        "0.2",
                        "50000"
                ))),
                context(Map.of("BINANCE_DERIVATIVE:USDT", new BigDecimal("100000")), snapshots)
        );

        assertThat(approved.isEmpty()).isTrue();
        assertThat(snapshots.getFirst().metrics().get("reasons").toString()).contains("max notional");
    }

    private ExecutionCommand.SubmitOrder submit(
            Exchange exchange,
            TradeType tradeType,
            String tradingPair,
            String amount,
            String price
    ) {
        return new ExecutionCommand.SubmitOrder(
                "funding-arb",
                exchange,
                "test",
                OrderCandidate.builder()
                        .tradingPair(tradingPair)
                        .orderType(OrderType.LIMIT)
                        .tradeType(tradeType)
                        .amount(new BigDecimal(amount))
                        .price(new BigDecimal(price))
                        .timeInForce(TimeInForce.GTC)
                        .build()
        );
    }

    private StrategyContext context(
            Map<String, BigDecimal> balances,
            List<StrategySnapshot> snapshots
    ) {
        return new StrategyContext(
                NOW,
                new StrategyMarketView() {},
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
