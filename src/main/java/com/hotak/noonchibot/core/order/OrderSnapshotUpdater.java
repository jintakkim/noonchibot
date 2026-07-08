package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderSnapshotUpdater implements EventHandler<OrderEvent.SnapshotUpdateRequested>, LifecycleAware {
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    public OrderSnapshotUpdater(
            OrderSnapshotRepository orderSnapshotRepository,
            EventSubscriber eventSubscriber
    ) {
        this.orderSnapshotRepository = orderSnapshotRepository;
        this.eventSubscriber = eventSubscriber;
    }

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

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                OrderEvent.SnapshotUpdateRequested.class,
                this,
                ExecutionPolicy.sequential()
        );
    }

    @Override
    public void onShutdown() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public int phase() {
        return Phases.SNAPSHOT_UPDATER_SETUP;
    }
}
