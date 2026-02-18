package com.hotak.noonchibot.connector.web;


import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import java.util.List;
import java.util.Map;
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
                new NoOpThrottler(),
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
                "/api/ticker", "limit1", HttpMethod.GET, false,
                Map.of("symbol", "BTCUSDT"), null, null
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
                "/api/order", "limit1", HttpMethod.POST, false,
                null, Map.of("side", "BUY"), null
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
                request -> RestRequest.builder()
                        .url(request.url())
                        .method(request.method())
                        .authRequired(true)
                        .headers(new HttpHeaders() {{ add("X-API-KEY", "test-key"); }})
                        .build(),
                new NoOpThrottler(),
                objectMapper
        );

        // when & then
        assertThatNoException().isThrownBy(() ->
                restAssistant.executeRequestAndGetJsonBody("/api/account", "limit1", HttpMethod.GET, true, null, null, null)
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
                null, new NoOpThrottler(), objectMapper
        );

        // when & then
        assertThatThrownBy(
                () -> restAssistant.executeRequestAndGetResponse("/api/orders", "limit1", HttpMethod.GET, false, null, null, null)
        ).isInstanceOf(RateLimitException.class);
    }



    class NoOpThrottler implements Throttler {
        @Override
        public <T> T execute(String limitId, Supplier<T> task) {
            return task.get();
        }
    }

    class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
