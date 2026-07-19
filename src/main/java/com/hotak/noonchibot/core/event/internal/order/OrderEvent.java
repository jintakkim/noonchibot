package com.hotak.noonchibot.core.event.internal.order;

import com.hotak.noonchibot.core.event.internal.CoreEvent;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderView;

import java.time.Instant;

public sealed interface OrderEvent extends CoreEvent {
    record ExchangeCreateRequested(InFlightOrder inFlightOrder) implements OrderEvent {}

    record ExchangeCancelRequested(String tradingPair, String clientOrderId, String exchangeOrderId) implements OrderEvent {}

    /**
     * 상태 업데이트 요청
     */
    record StatusUpdateRequested(
            String tradingPair,
            String clientOrderId
    ) implements OrderEvent {}

    record StatusPollingRequested(Exchange exchange) implements OrderEvent {}

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
    record SnapshotUpdateRequested(OrderView order) implements OrderEvent {}
}
