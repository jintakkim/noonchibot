package com.hotak.noonchibot.core.event.internal.order;

import com.hotak.noonchibot.core.event.internal.CoreEvent;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderState;

import java.time.Instant;

public sealed interface OrderEvent extends CoreEvent {
    /** 주문 생성 요청 */
    record CreateRequested(OrderCandidate candidate, String clientOrderId, Exchange exchange) implements OrderEvent {
        public CreateRequested(OrderCandidate candidate, String clientOrderId) {
            this(candidate, clientOrderId, null);
        }
    }

    /**
     * 취소 요청
     */
    record CancelRequested(String clientOrderId, Exchange exchange) implements OrderEvent {
        public CancelRequested(String clientOrderId) {
            this(clientOrderId, null);
        }
    }

    record ExchangeCreateRequested(InFlightOrder inFlightOrder) implements OrderEvent {}

    record ExchangeCancelRequested(String tradingPair, String clientOrderId, String exchangeOrderId) implements OrderEvent {}

    /**
     * 상태 업데이트 요청
     */
    record StatusUpdateRequested(
            String tradingPair,
            String clientOrderId
    ) implements OrderEvent {}

    record StatusReceived(
            String tradingPair,
            String clientOrderId,
            String exchangeOrderId,
            OrderState orderState,
            Instant timestamp
    ) implements OrderEvent {}

    /**
     * persist db 정보 업데이트 요청
     */
    record SnapshotUpdateRequested(
            String clientOrderId,
            String exchangeOrderId,
            OrderState orderState,
            Instant timestamp
    ) implements OrderEvent {}

    record Failed(
            String tradingPair,
            String clientOrderId,
            String exchangeOrderId,
            Throwable throwable
    ) implements OrderEvent {
    }
}
