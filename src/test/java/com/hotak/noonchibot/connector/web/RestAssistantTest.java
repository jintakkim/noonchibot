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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

@WireMockTest
public class RestAssistantTest {

    private RestAssistant restAssistant;
    private RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .baseUrl(wm.getHttpBaseUrl())
                .build();

        restAssistant = new RestAssistant(
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
        stubFor(post("/api/order")
                .withRequestBody(containing("BUY"))
                .willReturn(okJson("""
                        {"orderId":"123","status":"NEW"}
                        """)));

        RestResponse response = restAssistant.executeRequestAndGetResponse(
                RestRequest.builder()
                        .pathUrl("/api/order")
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

        restAssistant = new RestAssistant(
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

        restAssistant = new RestAssistant(
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
        restAssistant = new RestAssistant(
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
