package com.hotak.noonchibot.core.event;

import java.util.ArrayList;
import java.util.List;

public class TestEventSubscriber implements EventSubscriber {

    private final List<Subscribed<?>> subscriptions = new ArrayList<>();

    @Override
    public <E extends Event> Subscription subscribe(
            Class<E> eventType,
            EventHandler<E> listener,
            ExecutionPolicy policy
    ) {
        Subscribed<E> entry = new Subscribed<>(eventType, listener, policy);
        subscriptions.add(entry);
        return () -> subscriptions.remove(entry);  // Subscription.close()
    }

    /** 모든 구독 기록 */
    public List<Subscribed<?>> getSubscriptions() {
        return List.copyOf(subscriptions);
    }

    /** 특정 이벤트 타입에 대한 구독들 */
    @SuppressWarnings("unchecked")
    public <E extends Event> List<Subscribed<E>> getSubscriptionsFor(Class<E> eventType) {
        return subscriptions.stream()
                .filter(s -> s.eventType().equals(eventType))
                .map(s -> (Subscribed<E>) s)
                .toList();
    }

    /** 특정 이벤트 타입이 구독됐는지 확인 */
    public boolean isSubscribed(Class<? extends Event> eventType) {
        return subscriptions.stream()
                .anyMatch(s -> s.eventType().equals(eventType));
    }

    /** 구독 개수 */
    public int count() {
        return subscriptions.size();
    }

    /** 특정 이벤트 타입 구독 개수 */
    public int countFor(Class<? extends Event> eventType) {
        return (int) subscriptions.stream()
                .filter(s -> s.eventType().equals(eventType))
                .count();
    }

    /** 초기화 (테스트 사이) */
    public void clear() {
        subscriptions.clear();
    }

    public record Subscribed<E extends Event>(
            Class<E> eventType,
            EventHandler<E> handler,
            ExecutionPolicy policy
    ) {}
}