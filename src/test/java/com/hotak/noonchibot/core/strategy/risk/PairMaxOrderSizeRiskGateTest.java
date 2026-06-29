package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionUrgency;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PairMaxOrderSizeRiskGateTest {
    @Test
    void approve_resizesBothPairLegsToSameAmount() {
        PairMaxOrderSizeRiskGate gate = new PairMaxOrderSizeRiskGate(Map.of(
                "btc-pair", new BigDecimal("1")
        ));
        ExecutionPlan plan = new ExecutionPlan(List.of(
                submit(Exchange.BINANCE_DERIVATIVE, TradeType.BUY, "5"),
                submit(Exchange.HYPERLIQUID_DERIVATIVE, TradeType.SELL, "3")
        ));

        ExecutionPlan approved = gate.approve(plan, null);

        List<BigDecimal> amounts = approved.commands().stream()
                .map(ExecutionCommand.SubmitOrder.class::cast)
                .map(command -> command.candidate().getAmount())
                .toList();
        assertThat(amounts).containsExactly(new BigDecimal("1"), new BigDecimal("1"));
    }

    @Test
    void approve_doesNotResizeEmergencyStopLossOrders() {
        PairMaxOrderSizeRiskGate gate = new PairMaxOrderSizeRiskGate(Map.of(
                "btc-pair", new BigDecimal("1")
        ));
        ExecutionCommand.SubmitOrder stopLoss = submit(
                Exchange.BINANCE_DERIVATIVE,
                TradeType.SELL,
                "5",
                true,
                ExecutionUrgency.EMERGENCY
        );

        ExecutionPlan approved = gate.approve(new ExecutionPlan(List.of(stopLoss)), null);

        ExecutionCommand.SubmitOrder approvedStopLoss =
                (ExecutionCommand.SubmitOrder) approved.commands().getFirst();
        assertThat(approvedStopLoss.candidate().getAmount()).isEqualByComparingTo("5");
        assertThat(approvedStopLoss).isSameAs(stopLoss);
    }

    @Test
    void approve_resizesNormalReduceOnlyOrders() {
        PairMaxOrderSizeRiskGate gate = new PairMaxOrderSizeRiskGate(Map.of(
                "btc-pair", new BigDecimal("1")
        ));
        ExecutionCommand.SubmitOrder normalExit = submit(
                Exchange.BINANCE_DERIVATIVE,
                TradeType.SELL,
                "5",
                true,
                ExecutionUrgency.NORMAL
        );

        ExecutionPlan approved = gate.approve(new ExecutionPlan(List.of(normalExit)), null);

        ExecutionCommand.SubmitOrder resized =
                (ExecutionCommand.SubmitOrder) approved.commands().getFirst();
        assertThat(resized.candidate().getAmount()).isEqualByComparingTo("1");
    }

    private ExecutionCommand.SubmitOrder submit(
            Exchange exchange,
            TradeType tradeType,
            String amount
    ) {
        return submit(exchange, tradeType, amount, false, ExecutionUrgency.NORMAL);
    }

    private ExecutionCommand.SubmitOrder submit(
            Exchange exchange,
            TradeType tradeType,
            String amount,
            boolean reduceOnly,
            ExecutionUrgency urgency
    ) {
        return new ExecutionCommand.SubmitOrder(
                "arb",
                "btc-pair",
                exchange,
                "test",
                urgency,
                OrderCandidate.builder()
                        .tradingPair("BTC-USDT")
                        .orderType(OrderType.LIMIT)
                        .tradeType(tradeType)
                        .amount(new BigDecimal(amount))
                        .price(new BigDecimal("50000"))
                        .timeInForce(TimeInForce.GTC)
                        .reduceOnly(reduceOnly)
                        .build()
        );
    }
}
