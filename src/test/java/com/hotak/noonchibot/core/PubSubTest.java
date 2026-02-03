package com.hotak.noonchibot.core;

import com.hotak.noonchibot.core.event.EventListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class PubSubTest {
    @Test
    @DisplayName("Returns empty set when no listeners are added.")
    void no_listeners_added() {
        PubSub pubsub = new PubSub();

        Set<EventListener<TestEvent>> listeners = pubsub.getEventListeners(TestEvent.class);

        Assertions.assertThat(listeners).isEmpty();
    }

    @Test
    @DisplayName("Returns set with added listener only when a singular listener is added.")
    void add_listener() {
        PubSub pubsub = new PubSub();
        TestEventListener testEventListener1 = new TestEventListener();

        pubsub.addListener(TestEvent.class, testEventListener1);

        Set<EventListener<TestEvent>> listeners = pubsub.getEventListeners(TestEvent.class);

        Assertions.assertThat(listeners).containsExactly(testEventListener1);
    }

    @Test
    @DisplayName("Returns set with single listener only when same listener is added twice.")
    void add_same_listener_twice() {
        PubSub pubsub = new PubSub();
        TestEventListener testEventListener1 = new TestEventListener();

        pubsub.addListener(TestEvent.class, testEventListener1);
        Set<EventListener<TestEvent>> listeners = pubsub.getEventListeners(TestEvent.class);
        Assertions.assertThat(listeners).containsExactly(testEventListener1);

        pubsub.addListener(TestEvent.class, testEventListener1);
        Assertions.assertThat(listeners).containsExactly(testEventListener1);
    }

    @Test
    @DisplayName("Returns set with removed listener when certain listener is removed.")
    void remove_listener() {
        PubSub pubsub = new PubSub();
        TestEventListener testEventListener1 = new TestEventListener();
        TestEventListener testEventListener2 = new TestEventListener();

        pubsub.addListener(TestEvent.class, testEventListener1);
        pubsub.addListener(TestEvent.class, testEventListener2);

        pubsub.removeListener(TestEvent.class, testEventListener2);
        Set<EventListener<TestEvent>> listeners = pubsub.getEventListeners(TestEvent.class);
        Assertions.assertThat(listeners).containsExactly(testEventListener1);
    }

    @Test
    @DisplayName("Listeners added to different event types are kept separate.")
    void add_listeners_to_separate_events() {
        PubSub pubsub = new PubSub();
        TestEventListener testEventListener1 = new TestEventListener();
        AnotherTestEventListener testEventListener2 = new AnotherTestEventListener();

        pubsub.addListener(TestEvent.class, testEventListener1);
        pubsub.addListener(AnotherTestEvent.class, testEventListener2);

        Set<EventListener<TestEvent>> testEventListeners = pubsub.getEventListeners(TestEvent.class);
        Set<EventListener<AnotherTestEvent>> anotherEventListeners = pubsub.getEventListeners(AnotherTestEvent.class);

        Assertions.assertThat(testEventListeners).containsExactly(testEventListener1);
        Assertions.assertThat(anotherEventListeners).containsExactly(testEventListener2);
    }

    @Test
    @DisplayName("Triggering event calls only the relevant listener.")
    void trigger_event() {
        PubSub pubsub = new PubSub();
        TestEventListener testEventListener1 = new TestEventListener();
        AnotherTestEventListener testEventListener2 = new AnotherTestEventListener();

        pubsub.addListener(TestEvent.class, testEventListener1);
        pubsub.addListener(AnotherTestEvent.class, testEventListener2);

        pubsub.triggerEvent(new TestEvent());

        Assertions.assertThat(((TestEventListener) testEventListener1).called).isTrue();
        Assertions.assertThat(((AnotherTestEventListener) testEventListener2).called).isFalse();
    }

    static class TestEvent {}

    static class TestEventListener implements EventListener<TestEvent> {
        boolean called = false;

        @Override
        public void call(TestEvent event) {
            called = true;
        }
    }

    static class AnotherTestEvent {}

    static class AnotherTestEventListener implements EventListener<AnotherTestEvent> {
        boolean called = false;

        @Override
        public void call(AnotherTestEvent event) {
            called = true;
        }
    }
}
