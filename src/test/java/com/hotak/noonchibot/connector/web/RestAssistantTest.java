package com.hotak.noonchibot.connector.web;


import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.hotak.noonchibot.connector.ExchangeApiException;
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
    @DisplayName("4xx 에러 - throwError=true면 예외를 던진다")
    void clientError_throwErrorTrue_throwsException() {
        stubFor(get("/api/inFlightOrder")
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"code\":-2010,\"msg\":\"Insufficient balance\"}")));

        assertThatThrownBy(() ->
                restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/inFlightOrder")
                                .method(HttpMethod.GET)
                                .throwError(true)
                                .build()
                )
        ).isInstanceOf(ExchangeApiException.class)
                .satisfies(e -> {
                    ExchangeApiException ex = (ExchangeApiException) e;
                    assertThat(ex.httpStatusCode).isEqualTo(HttpStatusCode.valueOf(400));
                    assertThat(ex.getMessage()).contains("-2010");
                });
    }

    @Test
    @DisplayName("5xx 에러 - throwError=true면 예외를 던진다")
    void serverError_throwErrorTrue_throwsException() {
        stubFor(get("/api/inFlightOrder")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        assertThatThrownBy(() ->
                restAssistant.executeRequestAndGetResponse(
                        RestRequest.builder()
                                .pathUrl("/api/inFlightOrder")
                                .method(HttpMethod.GET)
                                .throwError(true)
                                .build()
                )
        ).isInstanceOf(ExchangeApiException.class)
                .satisfies(e -> {
                    ExchangeApiException ex = (ExchangeApiException) e;
                    assertThat(ex.httpStatusCode).isEqualTo(HttpStatusCode.valueOf(503));
                });
    }

    @Test
    @DisplayName("4xx 에러 - throwError=false면 예외 없이 응답을 반환한다")
    void clientError_throwErrorFalse_returnsResponse() {
        stubFor(get("/api/inFlightOrder")
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"code\":-2010,\"msg\":\"Insufficient balance\"}")));

        RestResponse response = restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/inFlightOrder")
                        .method(HttpMethod.GET)
                        .throwError(false)
                        .build()
        );

        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.body()).contains("-2010");
    }

    @Test
    @DisplayName("5xx 에러 - throwError=false면 예외 없이 응답을 반환한다")
    void serverError_throwErrorFalse_returnsResponse() {
        stubFor(get("/api/inFlightOrder")
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        RestResponse response = restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/inFlightOrder")
                        .method(HttpMethod.GET)
                        .throwError(false)
                        .build()
        );

        assertThat(response.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.body()).contains("Service Unavailable");
    }

    @Test
    @DisplayName("4xx 에러 - throwError=false여도 PostProcessor는 실행된다")
    void clientError_throwErrorFalse_postProcessorStillRuns() {
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
                                .throwError(false)
                                .build()
                )
        ).isInstanceOf(RateLimitException.class);
    }

    @Test
    @DisplayName("4xx 에러 - PostProcessor가 예외를 안 던지면 throwError에 따라 동작한다")
    void clientError_postProcessorPassesThrough_followsThrowError() {
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

        RestResponse response = restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/orders")
                        .method(HttpMethod.GET)
                        .throwError(false)
                        .build()
        );

        assertThat(postProcessorCalled).isTrue();
        assertThat(response.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }



    static class NoOpAsyncThrottler implements AsyncThrottler {
        public Map<String, Integer> weightOverrides;

        @Override
        public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task, Map<String, Integer> weightOverrides) {
            this.weightOverrides = weightOverrides;
            return CompletableFuture.completedFuture(task.get());
        }

        @Override
        public <T> CompletableFuture<T> execute(String limitId, Supplier<T> task) {
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

    static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
