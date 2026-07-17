package com.hotak.noonchibot.connector.web;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeProtocolException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestAssistantRetryBehaviorTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestRequest request = RestRequest.builder()
            .method(HttpMethod.GET)
            .pathUrl("/test")
            .build();

    @Test
    @DisplayName("응답 조회가 일시적으로 실패하면 설정한 횟수 안에서 재시도한다")
    void responsePath_retriesTransientFailureUntilSuccess() {
        ScriptedRestAssistant delegate = ScriptedRestAssistant.forResponses(
                transientApiFailure(),
                response("recovered")
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .maxAttempt(2)
                .build();

        RestResponse result = assistant.executeRequestAndGetResponse(request);

        assertThat(result.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.body()).isEqualTo("recovered");
        assertThat(delegate.responseCalls).isEqualTo(2);
    }

    @Test
    @DisplayName("재시도를 소진한 뒤에도 다음 논리 호출은 delegate에 전달한다")
    void retryExhaustion_doesNotBlockNextLogicalCall() {
        ScriptedRestAssistant delegate = ScriptedRestAssistant.forJsonBodies(
                transientApiFailure(),
                transientApiFailure(),
                transientApiFailure(),
                jsonBody()
        );
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .maxAttempt(3)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isInstanceOf(ExchangeTransientException.class);
        assertThat(delegate.jsonBodyCalls).isEqualTo(3);

        JsonNode result = assistant.executeRequestAndGetJsonBody(request);

        assertThat(result.get("ok").asBoolean()).isTrue();
        assertThat(delegate.jsonBodyCalls).isEqualTo(4);
    }

    @Test
    @DisplayName("프로토콜 오류는 재시도하지 않고 원래 예외를 전파한다")
    void protocolFailure_isNotRetried() {
        ExchangeProtocolException failure = new ExchangeProtocolException(
                "Invalid JSON response body",
                null
        );
        ScriptedRestAssistant delegate = ScriptedRestAssistant.forJsonBodies(failure);
        RestAssistant assistant = new RestAssistantBuilder(delegate)
                .maxAttempt(3)
                .build();

        assertThatThrownBy(() -> assistant.executeRequestAndGetJsonBody(request))
                .isSameAs(failure);

        assertThat(delegate.jsonBodyCalls).isEqualTo(1);
    }

    private static ExchangeApiException transientApiFailure() {
        return new ExchangeApiException(HttpStatus.INTERNAL_SERVER_ERROR, "temporary failure");
    }

    private static JsonNode jsonBody() {
        return OBJECT_MAPPER.readTree("{\"ok\":true}");
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
