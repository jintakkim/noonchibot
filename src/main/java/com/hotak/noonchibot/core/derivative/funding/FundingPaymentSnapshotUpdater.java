package com.hotak.noonchibot.core.derivative.funding;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventHandler;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.FundingPaymentEvent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FundingPaymentSnapshotUpdater implements
        LifecycleAware,
        EventHandler<FundingPaymentEvent.SnapshotUpdateRequested> {

    private final FundingPaymentRepository fundingPaymentRepository;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    @Override
    public void onEvent(FundingPaymentEvent.SnapshotUpdateRequested event) {
        fundingPaymentRepository.save(FundingPaymentHistory.from(event.payment()));
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                FundingPaymentEvent.SnapshotUpdateRequested.class,
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
        return Phases.SNAPSHOT_UPDATER_SETUP;
    }
}
