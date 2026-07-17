package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeProtocolException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestAssistantCircuitBehaviorTest {
    private final RestRequest request = RestRequest.builder()
            .method(HttpMethod.GET)
            .pathUrl("/test")
            .build();

    @Test
    @DisplayName("응답 조회 재시도가 성공하면 서킷 성공 호출 1회로 기록한다")
    void retrySuccess_isRecordedAsOneSuccessfulCircuitCall_forResponsePath() {
        CircuitBreakerRegistry registry = registry();
        ScriptedRestAssistant delegate = ScriptedRestAssistant.forResponses(
                transientApiFailure(),
                response("recovered")
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .circuit(registry, "BINANCE_SPOT.order-status")
                .maxAttempt(2)
                .build();

        RestResponse result = assistant.executeRequestAndGetResponse(request);

        CircuitBreaker circuit = registry.circuitBreaker("BINANCE_SPOT.order-status");
        assertThat(result.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.body()).isEqualTo("recovered");
        assertThat(delegate.responseCalls).isEqualTo(2);
        assertThat(circuit.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("재시도를 모두 소진하면 실패 1회로 기록하고 OPEN 이후 호출을 차단한다")
    void retryExhaustion_isRecordedAsOneFailedCircuitCall_andOpenCircuitBlocksRetryAndDelegate() {
        CircuitBreakerRegistry registry = registry();
        ScriptedRestAssistant delegate = ScriptedRestAssistant.forJsonBodies(
                transientApiFailure(),
                transientApiFailure(),
                transientApiFailure()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .circuit(registry, "BINANCE_SPOT.balance")
                .maxAttempt(3)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);

        CircuitBreaker circuit = registry.circuitBreaker("BINANCE_SPOT.balance");
        assertThat(delegate.jsonBodyCalls).isEqualTo(3);
        assertThat(circuit.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(CallNotPermittedException.class);

        assertThat(delegate.jsonBodyCalls).isEqualTo(3);
        assertThat(circuit.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("프로토콜 오류는 재시도하지 않고 서킷 실패 1회로 기록한다")
    void protocolFailure_isNotRetriedAndIsRecordedAsOneCircuitFailure() {
        CircuitBreakerRegistry registry = registry();
        ExchangeProtocolException failure = new ExchangeProtocolException(
                "Invalid JSON response body",
                null
        );
        ScriptedRestAssistant delegate = ScriptedRestAssistant.forJsonBodies(failure);
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .circuit(registry, "BINANCE_SPOT.order-status")
                .maxAttempt(3)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isSameAs(failure);

        CircuitBreaker circuit = registry.circuitBreaker("BINANCE_SPOT.order-status");
        assertThat(delegate.jsonBodyCalls).isEqualTo(1);
        assertThat(circuit.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(circuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("동일 registry의 서로 다른 서킷 이름은 상태를 공유하지 않는다")
    void differentCircuitNames_areIsolatedWithinTheSameRegistry() {
        CircuitBreakerRegistry registry = registry();
        ScriptedRestAssistant failingDelegate = ScriptedRestAssistant.forJsonBodies(transientApiFailure());
        ScriptedRestAssistant healthyDelegate = ScriptedRestAssistant.forResponses(response("available"));
        RestAssistant failingAssistant = new RestAssistantBuilder(failingDelegate)
                .circuit(registry, "BINANCE_SPOT.order-entry")
                .build();
        RestAssistant healthyAssistant = new RestAssistantBuilder(healthyDelegate)
                .circuit(registry, "BINANCE_SPOT.order-cancel")
                .build();

        assertThatThrownBy(() -> failingAssistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);

        RestResponse response = healthyAssistant.executeRequestAndGetResponse(request);

        CircuitBreaker failingCircuit = registry.circuitBreaker("BINANCE_SPOT.order-entry");
        CircuitBreaker healthyCircuit = registry.circuitBreaker("BINANCE_SPOT.order-cancel");
        assertThat(failingCircuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(healthyCircuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(failingCircuit.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(healthyCircuit.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(failingDelegate.jsonBodyCalls).isEqualTo(1);
        assertThat(healthyDelegate.responseCalls).isEqualTo(1);
        assertThat(response.body()).isEqualTo("available");
    }

    private static CircuitBreakerRegistry registry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .ignoreExceptions(ExchangeRejectedException.class)
                .recordExceptions(
                        ExchangeTransientException.class,
                        ExchangeProtocolException.class
                )
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    private static ExchangeApiException transientApiFailure() {
        return new ExchangeApiException(HttpStatus.INTERNAL_SERVER_ERROR, "temporary failure");
    }

    private static RestResponse response(String body) {
        return new RestResponse(HttpStatus.OK, new HttpHeaders(), body);
    }

    private static final class ScriptedRestAssistant implements RestAssistant {
        private final Queue<Object> jsonBodyResults;
        private final Queue<Object> responseResults;
        private int jsonBodyCalls;
        private int responseCalls;

        private ScriptedRestAssistant(Queue<Object> jsonBodyResults, Queue<Object> responseResults) {
            this.jsonBodyResults = jsonBodyResults;
            this.responseResults = responseResults;
        }

        private static ScriptedRestAssistant forJsonBodies(Object... results) {
            return new ScriptedRestAssistant(queueOf(results), new ArrayDeque<>());
        }

        private static ScriptedRestAssistant forResponses(Object... results) {
            return new ScriptedRestAssistant(new ArrayDeque<>(), queueOf(results));
        }

        @Override
        public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
            jsonBodyCalls++;
            return (JsonNode) next(jsonBodyResults);
        }

        @Override
        public RestResponse executeRequestAndGetResponse(RestRequest request) {
            responseCalls++;
            return (RestResponse) next(responseResults);
        }

        private static Queue<Object> queueOf(Object... results) {
            return new ArrayDeque<>(Arrays.asList(results));
        }

        private static Object next(Queue<Object> results) {
            Object result = results.remove();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return result;
        }
    }
}
