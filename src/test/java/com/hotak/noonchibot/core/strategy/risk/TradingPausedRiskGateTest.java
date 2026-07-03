package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.safety.TradingStatus;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradingPausedRiskGateTest {
    @Test
    void approve_whilePausedAllowsOnlyCancelAndReduceOnlyOrders() {
        ExecutionCommand.SubmitOrder entry = submit(false);
        ExecutionCommand.SubmitOrder reduction = submit(true);
        ExecutionCommand.CancelOrder cancellation = new ExecutionCommand.CancelOrder(
                "arb",
                Exchange.BINANCE_DERIVATIVE,
                "cancel",
                "cid-1"
        );
        TradingPausedRiskGate gate = new TradingPausedRiskGate(() -> TradingStatus.TRADING_PAUSED);

        ExecutionPlan approved = gate.approve(
                new ExecutionPlan(List.of(entry, reduction, cancellation)),
                null
        );

        assertThat(approved.commands()).containsExactly(reduction, cancellation);
    }

    private ExecutionCommand.SubmitOrder submit(boolean reduceOnly) {
        return new ExecutionCommand.SubmitOrder(
                "arb",
                "btc-pair",
                Exchange.BINANCE_DERIVATIVE,
                "test",
                OrderCandidate.builder()
                        .tradingPair("BTC-USDT")
                        .orderType(OrderType.LIMIT)
                        .tradeType(TradeType.SELL)
                        .amount(new BigDecimal("1"))
                        .price(new BigDecimal("50000"))
                        .timeInForce(TimeInForce.GTC)
                        .reduceOnly(reduceOnly)
                        .build()
        );
    }
}
