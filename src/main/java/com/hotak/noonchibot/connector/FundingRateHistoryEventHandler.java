package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.FailureAwareEventHandler;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;

import java.time.Instant;

public class FundingRateHistoryEventHandler implements FailureAwareEventHandler<FundingInfoEvent.HistoryFetchRequested>, LifecycleAware {
    private final FundingRateHistoryDataSource dataSource;
    private final EventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private Subscription subscription;

    public FundingRateHistoryEventHandler(
            FundingRateHistoryDataSource dataSource,
            EventPublisher eventPublisher,
            EventSubscriber eventSubscriber
    ) {
        this.dataSource = dataSource;
        this.eventPublisher = eventPublisher;
        this.eventSubscriber = eventSubscriber;
    }

    @Override
    public void onEvent(FundingInfoEvent.HistoryFetchRequested event) {
        eventPublisher.publish(new FundingInfoEvent.HistoryReceived(
                event.tradingPair(),
                event.fundingTime(),
                event.fundingInterval(),
                event.attempt(),
                dataSource.fetch(
                        event.tradingPair(),
                        event.fundingTime().minus(event.fundingInterval()),
                        Instant.now()
                )
        ));
    }

    @Override
    public void onFailure(FundingInfoEvent.HistoryFetchRequested event, Throwable cause) {
        eventPublisher.publish(new FundingInfoEvent.HistoryFetchFailed(
                event.tradingPair(),
                event.fundingTime(),
                event.fundingInterval(),
                event.attempt(),
                cause
        ));
    }

    @Override
    public void onStart() {
        subscription = eventSubscriber.subscribe(
                FundingInfoEvent.HistoryFetchRequested.class,
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
        return Phases.FUNDING_INFO_DATASOURCE_SETUP;
    }
}
