package com.hotak.noonchibot.connector.web;


import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
public class RestAssistantTest {

    private RestAssistantImpl restAssistant;
    private RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .baseUrl(wm.getHttpBaseUrl())
                .build();

        restAssistant = new RestAssistantImpl(
                restClient,
                List.of(),
                List.of(),
                null,
                new NoOpAsyncThrottler(),
                objectMapper
        );
    }

    @Test
    @DisplayName("GET 요청 - 200 응답 body를 JsonNode로 파싱한다")
    void get_returnsJsonBody() {
        // given
        stubFor(get(urlPathEqualTo("/api/ticker"))
                .withQueryParam("symbol", equalTo("BTCUSDT"))
                .willReturn(okJson("""
                       {"symbol":"BTCUSDT","price":"50000"}
                       """)));

        // when
        JsonNode result = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .pathUrl("/api/ticker")
                        .method(HttpMethod.GET)
                        .authRequired(false)
                        .params(Map.of("symbol", "BTCUSDT"))
                        .build()
        );
        // then
        assertThat(result.get("price").asString()).isEqualTo("50000");
    }


    @Test
    @DisplayName("POST 요청 - body를 전송하고 응답을 받는다")
    void post_sendsBodyAndReturnsResponse() {
        stubFor(post("/api/inFlightOrder")
                .withRequestBody(containing("BUY"))
                .willReturn(okJson("""
                        {"orderId":"123","status":"NEW"}
                        """)));

        RestResponse response = restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/inFlightOrder")
                        .method(HttpMethod.POST)
                        .authRequired(false)
                        .body(Map.of("side", "BUY"))
                        .build()
        );
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).contains("orderId");
    }

    @Test
    @DisplayName("인증 헤더가 요청에 포함된다")
    void auth_addsSignatureHeader() {
        // given
        stubFor(get("/api/account")
                .withHeader("X-API-KEY", equalTo("test-key"))
                .willReturn(okJson("{\"balance\":\"1000\"}")));

        restAssistant = new RestAssistantImpl(
                restClient, List.of(), List.of(),
                new TestAuthenticator(),
                new NoOpAsyncThrottler(),
                objectMapper
        );

        // when & then
        assertThatNoException().isThrownBy(() ->
                restAssistant.executeRequestAndGetJsonBody(
                        RestRequest.builder()
                                .pathUrl("/api/account")
                                .method(HttpMethod.GET)
                                .authRequired(true)
                                .build()
                )
        );
    }

    @Test
    @DisplayName("일부 200응답, body에러 일때 에러 감지 PostProcessor가 있다면 예외를 던진다")
    void postProcessor_throwsOnErrorStatus() {
        // given
        stubFor(get("/api/orders")
                .willReturn(aResponse().withStatus(200).withBody("{\"code\":-1003}")));

        restAssistant = new RestAssistantImpl(
                restClient,
                List.of(),
                List.of(response -> {
                    throw new RateLimitException("rate limit");
                }),
                null, new NoOpAsyncThrottler(), objectMapper
        );

        // when & then
        assertThatThrownBy(
                () -> restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/orders")
                                .method(HttpMethod.GET)
                                .authRequired(false)
                                .build()
                )
        ).isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("weightOverrides가 설정되면 throttler에 weight를 전달한다.")
    void weightOverrides_callsThrottlerWithWeight() {
        // given
        Map<String, Integer> weightOverrides = Map.of("test-weight", 1);

        stubFor(get(urlPathEqualTo("/api/ticker"))
                .willReturn(okJson("{\"symbol\":\"BTCUSDT\"}")));

        NoOpAsyncThrottler throttler = new NoOpAsyncThrottler();
        restAssistant = new RestAssistantImpl(
                restClient, List.of(), List.of(),
                null, throttler, objectMapper
        );

        // when
        restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/ticker")
                        .method(HttpMethod.GET)
                        .authRequired(false)
                        .weightOverrides(weightOverrides)
                        .build()
        );
        Assertions.assertThat(throttler.weightOverrides).containsEntry("test-weight", 1);

    }

    @Test
    @DisplayName("throttlerLimitId가 없으면 pathUrl을 사용한다")
    void missingThrottlerLimitId_usesPathUrl() {
        stubFor(get("/api/ticker").willReturn(okJson("{}")));
        NoOpAsyncThrottler throttler = new NoOpAsyncThrottler();
        restAssistant = new RestAssistantImpl(
                restClient, List.of(), List.of(), null, throttler, objectMapper
        );

        restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/ticker")
                        .method(HttpMethod.GET)
                        .build()
        );

        assertThat(throttler.limitId).isEqualTo("/api/ticker");
    }

    @Test
    @DisplayName("명시한 throttlerLimitId를 pathUrl보다 우선한다")
    void explicitThrottlerLimitId_takesPrecedence() {
        stubFor(get("/api/ticker").willReturn(okJson("{}")));
        NoOpAsyncThrottler throttler = new NoOpAsyncThrottler();
        restAssistant = new RestAssistantImpl(
                restClient, List.of(), List.of(), null, throttler, objectMapper
        );

        restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/ticker")
                        .method(HttpMethod.GET)
                        .throttlerLimitId("market-data")
                        .build()
        );

        assertThat(throttler.limitId).isEqualTo("market-data");
    }

    @Test
    @DisplayName("인증은 throttler 대기 이후 실제 호출 직전에 수행한다")
    void auth_runsAfterThrottlerSlotIsAcquired() {
        stubFor(get("/api/account")
                .withHeader("X-API-KEY", equalTo("test-key"))
                .willReturn(okJson("{\"balance\":\"1000\"}")));

        List<String> events = new ArrayList<>();
        restAssistant = new RestAssistantImpl(
                restClient,
                List.of(),
                List.of(),
                new RecordingAuthenticator(events),
                new RecordingAsyncThrottler(events),
                objectMapper
        );

        restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .pathUrl("/api/account")
                        .method(HttpMethod.GET)
                        .authRequired(true)
                        .build()
        );

        assertThat(events).containsExactly("throttled", "authenticated");
    }

    @Test
    @DisplayName("4xx 응답이면 REST API 예외를 던진다")
    void clientError_throwsException() {
        stubFor(get("/api/inFlightOrder")
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"code\":-2010,\"msg\":\"Insufficient balance\"}")));

        assertThatThrownBy(() ->
                restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/inFlightOrder")
                                .method(HttpMethod.GET)
                                .build()
                )
        ).isInstanceOf(ExchangeRejectedException.class)
                .satisfies(e -> {
                    ExchangeRestApiException ex = (ExchangeRestApiException) e.getCause();
                    assertThat(ex.statusCode()).isEqualTo(HttpStatusCode.valueOf(400));
                    assertThat(ex.responseBody()).contains("-2010");
                });
    }

    @Test
    @DisplayName("5xx 응답이면 REST API 예외를 던진다")
    void serverError_throwsException() {
        stubFor(get("/api/inFlightOrder")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        assertThatThrownBy(() ->
                restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/inFlightOrder")
                                .method(HttpMethod.GET)
                                .build()
                )
        ).isInstanceOf(ExchangeRestApiException.class)
                .satisfies(e -> {
                    ExchangeRestApiException ex = (ExchangeRestApiException) e;
                    assertThat(ex.statusCode()).isEqualTo(HttpStatusCode.valueOf(503));
                });
    }

    @Test
    @DisplayName("4xx 응답에서도 PostProcessor는 실행된다")
    void clientError_postProcessorStillRuns() {
        stubFor(get("/api/orders")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withBody("{\"code\":-1003,\"msg\":\"Too many requests\"}")));

        restAssistant = new RestAssistantImpl(
                restClient,
                List.of(),
                List.of(response -> {
                    if (response.body() != null && response.body().contains("-1003")) {
                        throw new RateLimitException("rate limit");
                    }
                    return response;
                }),
                null, new NoOpAsyncThrottler(), objectMapper
        );

        assertThatThrownBy(() ->
                restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/orders")
                                .method(HttpMethod.GET)
                                .build()
                )
        ).isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("PostProcessor가 통과시킨 4xx 응답은 REST API 예외가 된다")
    void clientError_postProcessorPassesThrough_throwsException() {
        AtomicBoolean postProcessorCalled = new AtomicBoolean(false);

        stubFor(get("/api/orders")
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"code\":-1021,\"msg\":\"Timestamp error\"}")));

        restAssistant = new RestAssistantImpl(
                restClient,
                List.of(),
                List.of(response -> {
                    postProcessorCalled.set(true);
                    return response;
                }),
                null, new NoOpAsyncThrottler(), objectMapper
        );

        assertThatThrownBy(() -> restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/orders")
                                .method(HttpMethod.GET)
                                .build()
                ))
                .isInstanceOf(ExchangeRejectedException.class);

        assertThat(postProcessorCalled).isTrue();
    }



    static class NoOpAsyncThrottler implements AsyncThrottler {
        public String limitId;
        public Map<String, Integer> weightOverrides;

        @Override
        public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task, Map<String, Integer> weightOverrides) {
            this.limitId = limitId;
            this.weightOverrides = weightOverrides;
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task) {
            this.limitId = limitId;
            return CompletableFuture.completedFuture(task.get());
        }
    }

    static class TestAuthenticator implements Authenticator {
        @Override
        public RestRequest restAuthenticate(RestRequest restRequest) {
            return RestRequest.builder()
                    .pathUrl(restRequest.pathUrl())
                    .method(restRequest.method())
                    .authRequired(true)
                    .headers(new HttpHeaders() {{ add("X-API-KEY", "test-key"); }})
                    .build();
        }

        @Override
        public WsRequest wsAuthenticate(WsRequest wsRequest) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    static class RecordingAuthenticator implements Authenticator {
        private final List<String> events;

        RecordingAuthenticator(List<String> events) {
            this.events = events;
        }

        @Override
        public RestRequest restAuthenticate(RestRequest restRequest) {
            events.add("authenticated");
            HttpHeaders headers = new HttpHeaders(restRequest.headers());
            headers.add("X-API-KEY", "test-key");
            return restRequest.toBuilder().headers(headers).build();
        }

        @Override
        public WsRequest wsAuthenticate(WsRequest wsRequest) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    static class RecordingAsyncThrottler implements AsyncThrottler {
        private final List<String> events;

        RecordingAsyncThrottler(List<String> events) {
            this.events = events;
        }

        @Override
        public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task, Map<String, Integer> weightOverrides) {
            events.add("throttled");
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task) {
            events.add("throttled");
            return CompletableFuture.completedFuture(task.get());
        }
    }

    static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
