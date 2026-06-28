package com.hotak.noonchibot.core.strategy.execution;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventPublishingExecutionPlanExecutorTest {
    @Test
    @DisplayName("execution command를 거래소가 포함된 order event로 변환한다")
    void execute_publishesExchangeScopedOrderEvents() {
        TestEventPublisher eventPublisher = new TestEventPublisher();
        EventPublishingExecutionPlanExecutor executor = new EventPublishingExecutionPlanExecutor(eventPublisher);
        OrderCandidate candidate = OrderCandidate.builder()
                .tradingPair("BTC-USDT")
                .orderType(OrderType.LIMIT)
                .tradeType(TradeType.BUY)
                .amount(new BigDecimal("0.1"))
                .price(new BigDecimal("50000"))
                .timeInForce(TimeInForce.GTC)
                .postOnly(true)
                .build();

        executor.execute(new ExecutionPlan(List.of(
                new ExecutionCommand.SubmitOrder(
                        "funding-arb",
                        Exchange.BINANCE_DERIVATIVE,
                        "long leg",
                        candidate
                ),
                new ExecutionCommand.CancelOrder(
                        "funding-arb",
                        Exchange.HYPERLIQUID_DERIVATIVE,
                        "stale order",
                        "cid-1"
                )
        )));

        OrderEvent.CreateRequested createRequested = eventPublisher.only(OrderEvent.CreateRequested.class);
        assertThat(createRequested.candidate()).isSameAs(candidate);
        assertThat(createRequested.clientOrderId()).isNull();
        assertThat(createRequested.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);

        OrderEvent.CancelRequested cancelRequested = eventPublisher.only(OrderEvent.CancelRequested.class);
        assertThat(cancelRequested.clientOrderId()).isEqualTo("cid-1");
        assertThat(cancelRequested.exchange()).isEqualTo(Exchange.HYPERLIQUID_DERIVATIVE);
    }
}
