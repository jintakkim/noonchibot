package com.hotak.noonchibot.core;

import com.hotak.noonchibot.core.event.SequentialDispatcher;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

public class RealtimeClock implements Clock, SmartLifecycle {
    private final TaskScheduler taskScheduler;
    private final Duration tickSize;
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private volatile ScheduledFuture<?> tickTask;

    public RealtimeClock(TaskScheduler taskScheduler, Duration tickSize) {
        this.taskScheduler = taskScheduler;
        this.tickSize = tickSize;
    }

    @Override
    public void addIterator(TimeIterator iterator, SequentialDispatcher dispatcher) {
        Registration registration = new Registration(iterator, dispatcher);
        registrations.add(registration);
        if (running) dispatchStart(registration, Instant.now());
    }

    @Override
    public void removeIterator(TimeIterator iterator) {
        registrations.stream()
                .filter(registration -> registration.iterator() == iterator)
                .findFirst()
                .ifPresent(registration -> {
                    registrations.remove(registration);
                    if (running) registration.dispatcher().dispatchSequential(iterator::onStop);
                });
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        Instant timestamp = Instant.now();
        registrations.forEach(registration -> dispatchStart(registration, timestamp));
        tickTask = taskScheduler.scheduleAtFixedRate(this::dispatchTick, tickSize);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        ScheduledFuture<?> task = tickTask;
        tickTask = null;
        if (task != null) task.cancel(false);
        registrations.forEach(registration ->
                registration.dispatcher().dispatchSequential(registration.iterator()::onStop)
        );
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void dispatchStart(Registration registration, Instant timestamp) {
        registration.dispatcher().dispatchSequential(() ->
                registration.iterator().onStart(this, timestamp)
        );
    }

    private void dispatchTick() {
        if (!running) return;
        Instant timestamp = Instant.now();
        registrations.forEach(registration ->
                registration.dispatcher().dispatchSequential(() ->
                        registration.iterator().onTick(timestamp)
                )
        );
    }

    private record Registration(
            TimeIterator iterator,
            SequentialDispatcher dispatcher
    ) {}
}
