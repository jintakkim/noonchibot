package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.connector.ExchangeAuthenticationException;
import com.hotak.noonchibot.connector.ExchangeRateLimitedException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.IGNORE;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.MANUAL_BLOCK;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.RECONCILE_AND_QUARANTINE;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.RECORD_FAILURE;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.AUTHENTICATION;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.CONFIRMED_REJECTION;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.RATE_LIMITED;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.TRANSIENT;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.UNKNOWN;

public class DefaultExchangeFailurePolicy implements ExchangeFailurePolicy {
    @Override
    public FailureDecision decide(ExchangeFailureEvent event) {
        Objects.requireNonNull(event, "event");

        Throwable cause = unwrap(event.cause());
        if (cause instanceof ExchangeAuthenticationException) {
            return new FailureDecision(MANUAL_BLOCK, AUTHENTICATION);
        }
        if (cause instanceof ExchangeRejectedException) {
            return new FailureDecision(IGNORE, CONFIRMED_REJECTION);
        }
        if (cause instanceof ExchangeRateLimitedException) {
            return transientDecision(event.operation(), RATE_LIMITED);
        }
        if (cause instanceof ExchangeTransientException) {
            return transientDecision(event.operation(), TRANSIENT);
        }
        if (event.operation() == ExchangeOperation.ORDER_PLACE) {
            return new FailureDecision(RECONCILE_AND_QUARANTINE, UNKNOWN);
        }
        return new FailureDecision(RECORD_FAILURE, UNKNOWN);
    }

    private FailureDecision transientDecision(
            ExchangeOperation operation,
            FailureDecision.Kind kind
    ) {
        FailureDecision.Action action = operation == ExchangeOperation.ORDER_PLACE
                ? RECONCILE_AND_QUARANTINE
                : RECORD_FAILURE;
        return new FailureDecision(action, kind);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
