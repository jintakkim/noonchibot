package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestAssistantBuilderTest {
    private final RestRequest request = RestRequest.builder()
            .method(HttpMethod.GET)
            .pathUrl("/test")
            .build();

    @Test
    @DisplayName("거절 오류는 분류하되 재시도하지 않는다")
    void rejectedError_isClassifiedWithoutRetry() {
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":-2010}"),
                jsonBody()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .errorClassifier(rejectedClassifier())
                .maxAttempt(3)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeRejectedException.class);

        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 가능 오류는 분류한 뒤 설정한 횟수 안에서 재시도한다")
    void retryableError_isClassifiedAndRetried() {
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":1202}"),
                jsonBody()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .errorClassifier(retryableClassifier())
                .maxAttempt(2)
                .build();

        JsonNode body = assistant.executeRequestAndGetJsonBody(request);

        assertThat(body.get("ok").asBoolean()).isTrue();
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("CompletionException으로 감싼 거래소 오류도 분류하고 재시도한다")
    void completionWrappedExchangeApiException_isUnwrappedClassifiedAndRetried() {
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new CompletionException(new ExchangeApiException(
                        HttpStatusCode.valueOf(500),
                        "temporary failure"
                )),
                jsonBody()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .maxAttempt(2)
                .build();

        JsonNode body = assistant.executeRequestAndGetJsonBody(request);

        assertThat(body.get("ok").asBoolean()).isTrue();
        assertThat(delegate.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("분류 결과가 재시도 대상이 아니면 첫 실패를 즉시 전파한다")
    void nonRetryableClassifiedError_isNotRetried() {
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(400), "{\"code\":-1021}"),
                jsonBody()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .errorClassifier(nonRetryableTransientClassifier())
                .maxAttempt(3)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(NonRetryableExchangeTransientException.class);

        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 시도 횟수를 설정하지 않으면 한 번만 호출한다")
    void defaultMaxAttempt_callsDelegateOnce() {
        SequencedRestAssistant delegate = new SequencedRestAssistant(
                new ExchangeApiException(HttpStatusCode.valueOf(500), "temporary failure"),
                jsonBody()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate).build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);

        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("음수 최대 시도 횟수는 허용하지 않는다")
    void negativeMaxAttempt_isRejected() {
        RestAssistantBuilder builder = new RestAssistantBuilder(new SequencedRestAssistant(jsonBody()));

        assertThatThrownBy(() -> builder.maxAttempt(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempt must not be negative");
    }

    private static JsonNode jsonBody() {
        return new ObjectMapper().readTree("{\"ok\":true}");
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
