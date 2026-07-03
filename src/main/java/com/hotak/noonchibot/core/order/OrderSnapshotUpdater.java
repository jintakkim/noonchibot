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
                .findById(event.order().clientOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "OrderSnapshot not found. clientOrderId=" + event.order().clientOrderId()
                ));
        snapshot.update(event.order());
        orderSnapshotRepository.save(snapshot);
    }
}
