package com.hotak.noonchibot.connector.transfer;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.*;
import com.hotak.noonchibot.core.event.internal.transfer.TransferEvent;
import com.hotak.noonchibot.core.transfer.FundTransferException;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class TransferDispatcher implements LifecycleAware, EventHandler<TransferEvent.Requested> {
    private final List<TransferHandler> handlers;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    @Override
    public void onEvent(TransferEvent.Requested event) {
        TransferRoute route = event.route();
        TransferHandler handler = handlers.stream()
                .filter(candidate -> candidate.canHandle(route))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            eventPublisher.publish(new TransferEvent.Failed(
                    route,
                    new FundTransferException("지원하지 않는 route 입니다. " + route)
            ));
            return;
        }

        try {
            eventPublisher.publish(new TransferEvent.Completed(handler.execute(route)));
        } catch (Exception e) {
            eventPublisher.publish(new TransferEvent.Failed(route, e));
        }
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                TransferEvent.Requested.class,
                this,
                ExecutionPolicy.concurrent()
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
        return Phases.TRANSFER_SETUP;
    }
}
