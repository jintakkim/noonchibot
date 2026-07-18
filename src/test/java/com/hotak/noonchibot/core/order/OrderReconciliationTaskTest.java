package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.exchange.ExchangeOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderReconciliationTaskTest {
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

    @Test
    @DisplayName("거래소 주문 생성 실패 이벤트로 영속 조정 작업을 생성한다")
    void fromCreatesPendingTask() {
        RuntimeException cause = new RuntimeException("응답 시간 초과");

        OrderReconciliationTask task = OrderReconciliationTask.from(failure(cause), NOW);

        assertThat(task.getId()).isNotBlank();
        assertThat(task.getExchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
        assertThat(task.getTradingPair()).isEqualTo("BTC-USDT");
        assertThat(task.getClientOrderId()).isEqualTo("cid-1");
        assertThat(task.getStrategyId()).isEqualTo("strategy-1");
        assertThat(task.getExecutionGroupId()).isEqualTo("group-1");
        assertThat(task.getStatus()).isEqualTo(OrderReconciliationStatus.PENDING);
        assertThat(task.getAttempts()).isZero();
        assertThat(task.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(task.getLastError()).contains("응답 시간 초과");
    }

    @Test
    @DisplayName("주문 상태를 확인하면 작업을 해결 상태로 종료한다")
    void resolveCompletesTask() {
        OrderReconciliationTask task = OrderReconciliationTask.from(failure(new RuntimeException("실패")), NOW);
        task.startAttempt(NOW.plusSeconds(1));
        OrderEvent.StatusReceived status = new OrderEvent.StatusReceived(
                "BTC-USDT",
                "cid-1",
                "eid-1",
                OrderState.OPEN,
                NOW.plusSeconds(2)
        );

        task.resolve(status, NOW.plusSeconds(2));

        assertThat(task.getStatus()).isEqualTo(OrderReconciliationStatus.RESOLVED);
        assertThat(task.getExchangeOrderId()).isEqualTo("eid-1");
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastError()).isNull();
        assertThat(task.getNextAttemptAt()).isNull();
        assertThat(task.getResolvedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    private ExchangeFailureEvent failure(Throwable cause) {
        return new ExchangeFailureEvent(
                Exchange.BINANCE_DERIVATIVE,
                ExchangeOperation.ORDER_PLACE,
                "BTC-USDT",
                "cid-1",
                null,
                "strategy-1",
                "group-1",
                cause,
                NOW
        );
    }
}
