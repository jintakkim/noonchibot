package com.hotak.noonchibot.core.strategy.risk;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.exchange.ExchangeEligibilityView;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeEligibilityRiskGateTest {
    private ExchangeEligibilityView eligibilityView;
    private ExchangeEligibilityRiskGate riskGate;

    @BeforeEach
    void setUp() {
        eligibilityView = mock(ExchangeEligibilityView.class);
        riskGate = new ExchangeEligibilityRiskGate(eligibilityView);
    }

    @Test
    @DisplayName("같은 실행 그룹의 신규 주문 중 한 거래소라도 부적격이면 양쪽 신규 주문을 모두 차단한다")
    void approve_whenOneExchangeIsIneligible_removesAllNewOrdersInExecutionGroup() {
        allow(Exchange.BINANCE_DERIVATIVE);
        block(Exchange.HYPERLIQUID_DERIVATIVE);
        ExecutionCommand.SubmitOrder eligibleLeg = submit(
                "group-1",
                Exchange.BINANCE_DERIVATIVE,
                false
        );
        ExecutionCommand.SubmitOrder ineligibleLeg = submit(
                "group-1",
                Exchange.HYPERLIQUID_DERIVATIVE,
                false
        );
        ExecutionCommand.SubmitOrder otherGroup = submit(
                "group-2",
                Exchange.BINANCE_DERIVATIVE,
                false
        );

        ExecutionPlan approved = riskGate.approve(
                new ExecutionPlan(List.of(eligibleLeg, ineligibleLeg, otherGroup)),
                null
        );

        assertThat(approved.commands()).containsExactly(otherGroup);
    }

    @Test
    @DisplayName("부적격 거래소의 reduce-only 주문과 취소 요청은 그대로 허용한다")
    void approve_whenExchangeIsIneligible_allowsReduceOnlyAndCancel() {
        allow(Exchange.BINANCE_DERIVATIVE);
        block(Exchange.HYPERLIQUID_DERIVATIVE);
        ExecutionCommand.SubmitOrder eligibleNewOrder = submit(
                "group-1",
                Exchange.BINANCE_DERIVATIVE,
                false
        );
        ExecutionCommand.SubmitOrder ineligibleReduceOnly = submit(
                "group-1",
                Exchange.HYPERLIQUID_DERIVATIVE,
                true
        );
        ExecutionCommand.CancelOrder cancel = new ExecutionCommand.CancelOrder(
                "strategy-1",
                Exchange.HYPERLIQUID_DERIVATIVE,
                "exit",
                "cid-1"
        );

        ExecutionPlan approved = riskGate.approve(
                new ExecutionPlan(List.of(eligibleNewOrder, ineligibleReduceOnly, cancel)),
                null
        );

        assertThat(approved.commands()).containsExactly(eligibleNewOrder, ineligibleReduceOnly, cancel);
    }

    @Test
    @DisplayName("실행 그룹이 없는 신규 주문은 다른 그룹 없는 주문과 묶지 않고 개별 판단한다")
    void approve_whenExecutionGroupIdIsNull_blocksOnlyTheIneligibleOrder() {
        allow(Exchange.BINANCE_DERIVATIVE);
        block(Exchange.HYPERLIQUID_DERIVATIVE);
        ExecutionCommand.SubmitOrder eligibleOrder = submit(
                null,
                Exchange.BINANCE_DERIVATIVE,
                false
        );
        ExecutionCommand.SubmitOrder ineligibleOrder = submit(
                null,
                Exchange.HYPERLIQUID_DERIVATIVE,
                false
        );

        ExecutionPlan approved = riskGate.approve(
                new ExecutionPlan(List.of(eligibleOrder, ineligibleOrder)),
                null
        );

        assertThat(approved.commands()).containsExactly(eligibleOrder);
    }

    @Test
    @DisplayName("부적격 거래소의 신규 주문 변경은 차단하고 reduce-only 변경은 허용한다")
    void approve_whenModifyingOrderOnIneligibleExchange_allowsOnlyReduceOnlyModification() {
        allow(Exchange.BINANCE_DERIVATIVE);
        block(Exchange.HYPERLIQUID_DERIVATIVE);
        ExecutionCommand.ModifyOrder ineligibleNewOrder = modify(
                Exchange.HYPERLIQUID_DERIVATIVE,
                false,
                "cid-1"
        );
        ExecutionCommand.ModifyOrder ineligibleReduceOnly = modify(
                Exchange.HYPERLIQUID_DERIVATIVE,
                true,
                "cid-2"
        );
        ExecutionCommand.ModifyOrder eligibleNewOrder = modify(
                Exchange.BINANCE_DERIVATIVE,
                false,
                "cid-3"
        );

        ExecutionPlan approved = riskGate.approve(
                new ExecutionPlan(List.of(ineligibleNewOrder, ineligibleReduceOnly, eligibleNewOrder)),
                null
        );

        assertThat(approved.commands()).containsExactly(ineligibleReduceOnly, eligibleNewOrder);
    }

    private ExecutionCommand.SubmitOrder submit(
            String executionGroupId,
            Exchange exchange,
            boolean reduceOnly
    ) {
        return new ExecutionCommand.SubmitOrder(
                "strategy-1",
                executionGroupId,
                exchange,
                "rebalance",
                candidate(reduceOnly)
        );
    }

    private ExecutionCommand.ModifyOrder modify(
            Exchange exchange,
            boolean reduceOnly,
            String clientOrderId
    ) {
        return new ExecutionCommand.ModifyOrder(
                "strategy-1",
                exchange,
                "reprice",
                clientOrderId,
                candidate(reduceOnly)
        );
    }

    private OrderCandidate candidate(boolean reduceOnly) {
        return OrderCandidate.builder()
                .tradingPair("BTC-USDT")
                .orderType(OrderType.LIMIT)
                .tradeType(TradeType.BUY)
                .amount(new BigDecimal("0.1"))
                .price(new BigDecimal("50000"))
                .timeInForce(TimeInForce.GTC)
                .reduceOnly(reduceOnly)
                .build();
    }

    private void allow(Exchange exchange) {
        when(eligibilityView.isEligible(exchange)).thenReturn(true);
    }

    private void block(Exchange exchange) {
        when(eligibilityView.isEligible(exchange)).thenReturn(false);
    }
}
