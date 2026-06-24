package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class OrderSnapshotUpdater implements EventHandler<OrderEvent.SnapshotUpdateRequested> {
    private final OrderSnapshotRepository orderSnapshotRepository;

    @Override
    public void onEvent(OrderEvent.SnapshotUpdateRequested event) {
        OrderSnapshot snapshot = orderSnapshotRepository
                .findById(event.clientOrderId())
                .orElseThrow(() -> new IllegalStateException("OrderSnapshot not found. clientOrderId=" + event.clientOrderId()));
        if(snapshot.getExchangeOrderId() == null) snapshot.setExchangeOrderId(event.clientOrderId());
        snapshot.setState(event.orderState());
        snapshot.setUpdatedAt(event.timestamp());
        orderSnapshotRepository.save(snapshot);
    }
}
