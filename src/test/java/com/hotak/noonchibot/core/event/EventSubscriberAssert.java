package com.hotak.noonchibot.core.event;

import org.assertj.core.api.AbstractAssert;

public class EventSubscriberAssert extends AbstractAssert<EventSubscriberAssert, TestEventSubscriber> {

    public EventSubscriberAssert(TestEventSubscriber actual) {
        super(actual, EventSubscriberAssert.class);
    }

    public static EventSubscriberAssert assertThat(TestEventSubscriber actual) {
        return new EventSubscriberAssert(actual);
    }

    public EventSubscriberAssert hasSubscriptionCount(int expected) {
        isNotNull();
        if (actual.count() != expected) {
            failWithMessage("Expected %d subscriptions but got %d",
                    expected, actual.count());
        }
        return this;
    }

    public EventSubscriberAssert isSubscribedTo(Class<? extends Event> eventType) {
        isNotNull();
        if (!actual.isSubscribed(eventType)) {
            failWithMessage("Expected subscription for <%s> but not found. Subscribed: %s",
                    eventType.getSimpleName(),
                    actual.getSubscriptions().stream()
                            .map(s -> s.eventType().getSimpleName())
                            .toList());
        }
        return this;
    }

    public EventSubscriberAssert isNotSubscribedTo(Class<? extends Event> eventType) {
        isNotNull();
        if (actual.isSubscribed(eventType)) {
            failWithMessage("Expected no subscription for <%s> but found one",
                    eventType.getSimpleName());
        }
        return this;
    }

    public SubscriptionAssert subscription(Class<? extends Event> eventType) {
        isNotNull();
        var sub = actual.exactlyOne(eventType);
        return new SubscriptionAssert(sub);
    }

    public EventSubscriberAssert hasNoSubscriptions() {
        return hasSubscriptionCount(0);
    }
}