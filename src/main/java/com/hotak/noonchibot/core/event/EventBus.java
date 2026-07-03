package com.hotak.noonchibot.core.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Slf4j
public class EventBus implements EventPublisher, EventSubscriber, SequentialDispatcher {
    public static final int DEFAULT_SEQ_CAPACITY = 1000;

    private final Map<String, Sequencer> sequencers = new ConcurrentHashMap<>();
    private final ExecutorService concurrentExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<Class<? extends Event>, List<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();
    private final int seqCapacity;

    public EventBus(int seqCapacity) {
        this.seqCapacity = seqCapacity;
    }

    public EventBus() {
        this(DEFAULT_SEQ_CAPACITY);
    }

    @Override
    public <E extends Event> Subscription subscribe(
            Class<E> type,
            EventHandler<E> handler,
        ExecutionPolicy policy
    ) {
        if (policy instanceof ExecutionPolicy.Sequential s) {
            sequencer(s.key());
        }
        HandlerEntry<E> entry = new HandlerEntry<>(handler, policy);
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(entry);
        return () -> handlers.getOrDefault(type, List.of()).remove(entry);
    }

    @Override
    public void publish(Event event, EventMetadata metadata) {
        List<HandlerEntry<?>> entries = handlers.get(event.getClass());
        if (entries == null || entries.isEmpty()) {
            log.warn(
                    "No handlers registered: type={}, event={}, corrId={}, timestamp={}",
                    event.getClass().getName(),
                    event,
                    metadata.corrId(),
                    metadata.timestamp()
            );
            return;
        }
        for (HandlerEntry<?> raw : entries) {
            @SuppressWarnings("unchecked")
            var entry = (HandlerEntry<Event>) raw;
            dispatch(entry, event, metadata);
        }
    }

    @Override
    public void publish(Event event) {
        publish(event, EventContext.currentOrRoot().derive());
    }

    @Override
    public void dispatchSequential(Runnable task) {
        sequencer(EventConstants.DOMAIN_KEY).submit(task);
    }

    private void dispatch(HandlerEntry<Event> entry, Event event, EventMetadata metadata) {
        Runnable task = () -> EventContext.runWith(metadata, () -> {
            try {
                entry.handler().onEvent(event);
            } catch (Exception e) {
                if(entry.handler instanceof FailureAwareEventHandler<?> failureAwareHandler) {
                    @SuppressWarnings("unchecked")
                    var typed = (FailureAwareEventHandler<Event>) failureAwareHandler;
                    typed.onFailure(event, e);
                    return;
                }
                log.error("failed to handle event", e);
            }
        });
        switch (entry.policy()) {
            case ExecutionPolicy.Sequential s -> sequencer(s.key()).submit(task);
            case ExecutionPolicy.Concurrent c -> concurrentExecutor.submit(task);
            case ExecutionPolicy.Inline i     -> task.run();
        }
    }

    private Sequencer sequencer(String key) {
        return sequencers.computeIfAbsent(key, k -> {
            Sequencer created = new Sequencer(k, seqCapacity);
            created.start();
            return created;
        });
    }

    private record HandlerEntry<E extends Event>(
            EventHandler<E> handler,
            ExecutionPolicy policy
    ) {}
}
