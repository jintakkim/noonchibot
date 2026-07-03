package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class OrderRecoveryBootstrap implements LifecycleAware {
    private static final List<OrderState> TERMINAL_STATES = Arrays.stream(OrderState.values())
            .filter(OrderState::isTerminal)
            .toList();

    private final Exchange exchange;
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final OrderStatusReader orderStatusReader;
    private final OrderTradeReader orderTradeReader;
    private final OrderTracker orderTracker;

    @Override
    public void onStart() {
        List<OrderSnapshot> snapshots = orderSnapshotRepository.findByExchangeAndStateNotIn(
                exchange,
                TERMINAL_STATES
        );
        for (OrderSnapshot snapshot : snapshots) {
            InFlightOrder order = snapshot.toInFlightOrder();
            orderTracker.restore(order);
            reconcile(order.toView());
        }
    }

    private void reconcile(OrderView order) {
        OrderEvent.StatusReceived status = orderStatusReader.fetch(
                order.tradingPair(),
                order.clientOrderId()
        );
        String exchangeOrderId = status.exchangeOrderId() == null
                ? order.exchangeOrderId()
                : status.exchangeOrderId();
        if (exchangeOrderId != null) {
            orderTracker.reconcile(orderTradeReader.fetch(
                    order.clientOrderId(),
                    exchangeOrderId,
                    order.tradingPair()
            ));
        }
        orderTracker.reconcile(status);
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public int phase() {
        return Phases.ORDER_RESTORE;
    }
}
