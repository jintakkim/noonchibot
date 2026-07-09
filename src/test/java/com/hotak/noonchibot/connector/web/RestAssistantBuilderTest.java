package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestAssistantBuilderTest {
    private final RestRequest request = RestRequest.builder()
            .method(HttpMethod.GET)
            .pathUrl("/test")
            .build();

    @Test
    void errorClassifier_canIgnoreRejectedErrorWithoutOpeningCircuit() {
        CircuitBreakerRegistry registry = registry();
        RestAssistant assistant = new RestAssistantBuilder(new ThrowingRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":-2010}")
        ))
                .circuit(registry, "BINANCE_SPOT.order-entry")
                .errorClassifier(rejectedClassifier())
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeRejectedException.class);

        assertThat(registry.circuitBreaker("BINANCE_SPOT.order-entry").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void errorClassifier_canRetryWithinSingleCircuitCall() {
        CircuitBreakerRegistry registry = registry();
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":1202}"),
                new ObjectMapper().readTree("{\"ok\":true}")
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .circuit(registry, "BINANCE_SPOT.order-status")
                .errorClassifier(retryableClassifier())
                .maxAttempt(2)
                .build();

        JsonNode body = assistant.executeRequestAndGetJsonBody(request);

        assertThat(body.get("ok").asBoolean()).isTrue();
        assertThat(delegate.calls).isEqualTo(2);
        assertThat(registry.circuitBreaker("BINANCE_SPOT.order-status").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void errorClassifier_canRecordFailureAndOpenCircuit() {
        CircuitBreakerRegistry registry = registry();
        RestAssistant assistant = new RestAssistantBuilder(new ThrowingRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":-1021}")
        ))
                .circuit(registry, "BINANCE_SPOT.server-time")
                .errorClassifier(nonRetryableTransientClassifier())
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);

        assertThat(registry.circuitBreaker("BINANCE_SPOT.server-time").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void errorClassifier_recordFailureDoesNotRetry() {
        CircuitBreakerRegistry registry = registry();
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":-1021}"),
                new ObjectMapper().readTree("{\"ok\":true}")
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .circuit(registry, "BINANCE_SPOT.server-time")
                .errorClassifier(nonRetryableTransientClassifier())
                .maxAttempt(1)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);

        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    void openCircuit_blocksNextCallBeforeDelegate() {
        CircuitBreakerRegistry registry = registry();
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(500), "{\"code\":-1000}"),
                new ObjectMapper().readTree("{\"ok\":true}")
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .circuit(registry, "BINANCE_SPOT.balance")
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);
        assertThat(registry.circuitBreaker("BINANCE_SPOT.balance").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(delegate.calls).isEqualTo(1);
    }

    private CircuitBreakerRegistry registry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .ignoreExceptions(ExchangeRejectedException.class)
                .recordExceptions(ExchangeTransientException.class)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    private static ExchangeErrorClassifier rejectedClassifier() {
        return new ExchangeErrorClassifier() {
            @Override
            public RuntimeException classify(ExchangeApiException exception) {
                return new ExchangeRejectedException(exception);
            }

            @Override
            public boolean test(Throwable throwable) {
                return false;
            }
        };
    }

    private static ExchangeErrorClassifier retryableClassifier() {
        return new ExchangeErrorClassifier() {
            @Override
            public RuntimeException classify(ExchangeApiException exception) {
                return new ExchangeTransientException(exception);
            }

            @Override
            public boolean test(Throwable throwable) {
                return throwable instanceof ExchangeTransientException;
            }
        };
    }

    private static ExchangeErrorClassifier nonRetryableTransientClassifier() {
        return new ExchangeErrorClassifier() {
            @Override
            public RuntimeException classify(ExchangeApiException exception) {
                return new NonRetryableExchangeTransientException(exception);
            }

            @Override
            public boolean test(Throwable throwable) {
                return false;
            }
        };
    }

    private static class NonRetryableExchangeTransientException extends ExchangeTransientException {
        private NonRetryableExchangeTransientException(ExchangeApiException cause) {
            super(cause);
        }
    }

    private static class ThrowingRestAssistant implements RestAssistant {
        private final RuntimeException exception;

        private ThrowingRestAssistant(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
            throw exception;
        }

        @Override
        public RestResponse executeRequestAndGetResponse(RestRequest request) {
            throw exception;
        }
    }

    private static class SequencedRestAssistant implements RestAssistant {
        private final Queue<Object> results = new ArrayDeque<>();
        private int calls;

        private SequencedRestAssistant(Object... results) {
            this.results.addAll(java.util.List.of(results));
        }

        @Override
        public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
            calls++;
            Object result = results.remove();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return (JsonNode) result;
        }

        @Override
        public RestResponse executeRequestAndGetResponse(RestRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
