package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.FundingPaymentEvent;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class FundingPaymentTracker implements LifecycleAware {
    private final PositionTracker positionTracker;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final Set<Subscription> subscriptions = new HashSet<>();

    @VisibleForTesting
    void processFundingPayment(FundingPaymentEvent.Received event) {
        FundingPayment payment = event.payment();
        positionTracker.applyFundingPayment(payment);
        eventPublisher.publish(new FundingPaymentEvent.SnapshotUpdateRequested(payment));
    }

    @Override
    public void onStart() {
        subscriptions.add(eventSubscriber.subscribe(
                FundingPaymentEvent.Received.class,
                this::processFundingPayment,
                ExecutionPolicy.sequential()
        ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    @Override
    public int phase() {
        return Phases.FUNDING_PAYMENT_TRACKER_SETUP;
    }
}
