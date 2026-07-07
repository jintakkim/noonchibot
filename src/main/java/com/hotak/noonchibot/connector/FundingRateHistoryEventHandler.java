package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.FailureAwareEventHandler;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;

import java.time.Instant;

public record FundingRateHistoryEventHandler(
        FundingRateHistoryDataSource dataSource,
        EventPublisher eventPublisher
) implements FailureAwareEventHandler<FundingInfoEvent.HistoryFetchRequested> {
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
}
