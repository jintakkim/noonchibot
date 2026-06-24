package com.hotak.noonchibot.core.event;

import org.assertj.core.api.AbstractAssert;

public class SubscriptionAssert extends AbstractAssert<SubscriptionAssert, TestEventSubscriber.Subscribed<?>> {

    public SubscriptionAssert(TestEventSubscriber.Subscribed<?> actual) {
        super(actual, SubscriptionAssert.class);
    }

    public SubscriptionAssert usesConcurrentPolicy() {
        isNotNull();
        if (!(actual.policy() instanceof ExecutionPolicy.Concurrent)) {
            failWithMessage("Expected Concurrent policy but got <%s>",
                    actual.policy().getClass().getSimpleName());
        }
        return this;
    }

    public SubscriptionAssert usesSequentialPolicy(String expectedKey) {
        isNotNull();
        if (!(actual.policy() instanceof ExecutionPolicy.Sequential seq)) {
            failWithMessage("Expected Sequential policy but got <%s>",
                    actual.policy().getClass().getSimpleName());
            return this;
        }
        if (!seq.key().equals(expectedKey)) {
            failWithMessage("Expected Sequential key <%s> but got <%s>",
                    expectedKey, seq.key());
        }
        return this;
    }

    public SubscriptionAssert usesInlinePolicy() {
        isNotNull();
        if (!(actual.policy() instanceof ExecutionPolicy.Inline)) {
            failWithMessage("Expected Inline policy but got <%s>",
                    actual.policy().getClass().getSimpleName());
        }
        return this;
    }

    public SubscriptionAssert hasHandler(EventHandler<?> expected) {
        isNotNull();
        if (actual.handler() != expected) {
            failWithMessage("Expected same handler instance but was different");
        }
        return this;
    }
}
