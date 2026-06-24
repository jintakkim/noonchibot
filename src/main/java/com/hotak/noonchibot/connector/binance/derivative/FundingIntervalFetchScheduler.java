package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;

@Slf4j
class FundingIntervalFetchScheduler implements LifecycleAware {
    private static final Duration REFRESH_INTERVAL = Duration.ofHours(1);

    private final TaskScheduler taskScheduler;
    private final EventPublisher eventPublisher;
    private volatile ScheduledFuture<?> task;

    public FundingIntervalFetchScheduler(
            TaskScheduler taskScheduler,
            EventPublisher eventPublisher
    ) {
        this.taskScheduler = taskScheduler;
        this.eventPublisher = eventPublisher;
    }

    private void triggerRefresh() {
        eventPublisher.publish(new FundingInfoEvent.IntervalRestFetchRequested());
    }

    @Override
    public void onStart() {
        triggerRefresh();
        Instant nextRefresh = Instant.now()
                .atZone(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .plusMinutes(1)
                .toInstant();

        task = taskScheduler.scheduleAtFixedRate(
                this::triggerRefresh,
                nextRefresh,
                REFRESH_INTERVAL
        );
        log.debug("FundingIntervalFetchScheduler started, next refresh at {}", nextRefresh);
    }

    @Override
    public void onShutdown() {
        if(task != null) {
            task.cancel(true);
            task = null;
        }
    }

    @Override
    public int phase() {
        return Phases.FUNDING_INFO_DATASOURCE_SETUP;
    }
}
