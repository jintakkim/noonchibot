package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.order.OrderSnapshot;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import lombok.RequiredArgsConstructor;

//@RequiredArgsConstructor
//public class OrderRecoveryBootstrap implements LifecycleAware {
//    private final OrderSnapshotRepository orderSnapshotRepository;
//    private final DerivativeOrderStatusReader orderStatusReader;
//    private final OrderTracker orderTracker;
//
//    @Override
//    public void onStart() {
//        orderSnapshotRepository.findByActiveTrue()
//                .forEach(snapshot -> {
//                    OrderSnapshot status = orderStatusReader.fetch(
//                            snapshot.getTradingPair(),
//                            snapshot.getExchangeSymbol(),
//                            snapshot.getClientOrderId()
//                    );
//
//                    snapshot.applyStatus(
//                            status.exchangeOrderId(),
//                            status.orderState(),
//                            status.timestamp()
//                    );
//
//                    if (!status.orderState().isTerminal()) {
//                        orderTracker.restore(status.toInFlightOrder());
//                    }
//                });
//    }
//
//    @Override
//    public int phase() {
//        return Phases.ORDER_RECOVERY;
//    }
//
//    @Override
//    public void onShutdown() {
//
//
//    }
