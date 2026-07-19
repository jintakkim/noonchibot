package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.web.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceTimestampRecoveringRestAssistantTest {
    private RestAssistant delegate;
    private TimeSynchronizer timeSynchronizer;
    private TimestampRecoveringRestAssistant restAssistant;

    @BeforeEach
    void setUp() {
        delegate = mock(RestAssistant.class);
        timeSynchronizer = mock(TimeSynchronizer.class);
        restAssistant = new TimestampRecoveringRestAssistant(
                delegate,
                timeSynchronizer,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("인증 요청에서 timestamp 오류 예외가 발생하면 서버 시간을 갱신하고 한 번 재시도한다")
    void executeRequestAndGetResponse_whenTimestampException_refreshesServerTimeAndRetries() {
        RestRequest request = signedRequest();
        RestResponse success = okResponse();
        when(delegate.executeRequestAndGetResponse(request))
                .thenThrow(timestampException())
                .thenReturn(success);

        RestResponse response = restAssistant.executeRequestAndGetResponse(request);

        assertThat(response).isSameAs(success);
        verify(timeSynchronizer).updateServerTimeOffset();
        verify(delegate, times(2)).executeRequestAndGetResponse(request);
    }

    @Test
    @DisplayName("비인증 요청의 timestamp 오류는 재시도하지 않는다")
    void executeRequestAndGetResponse_whenUnauthenticatedRequest_doesNotRetry() {
        RestRequest request = RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/v3/time")
                .authRequired(false)
                .build();
        ExchangeRestApiException exception = timestampException();
        when(delegate.executeRequestAndGetResponse(request)).thenThrow(exception);

        assertThatThrownBy(() -> restAssistant.executeRequestAndGetResponse(request))
                .isSameAs(exception);
        verify(timeSynchronizer, never()).updateServerTimeOffset();
        verify(delegate).executeRequestAndGetResponse(request);
    }

    @Test
    @DisplayName("JSON body 요청도 공통 응답 retry 경로를 탄다")
    void executeRequestAndGetJsonBody_usesRecoveringResponsePath() {
        RestRequest request = signedRequest();
        when(delegate.executeRequestAndGetResponse(request))
                .thenThrow(timestampException())
                .thenReturn(new RestResponse(HttpStatus.OK, new HttpHeaders(), "{\"ok\":true}"));

        assertThat(restAssistant.executeRequestAndGetJsonBody(request).get("ok").asBoolean()).isTrue();
        verify(timeSynchronizer).updateServerTimeOffset();
        verify(delegate, times(2)).executeRequestAndGetResponse(request);
        verify(delegate, never()).executeRequestAndGetJsonBody(request);
    }

    @Test
    @DisplayName("시간 동기화 후에도 timestamp 오류가 발생하면 동기화 실패 예외를 던진다")
    void executeRequestAndGetResponse_whenRetryAlsoFailsWithTimestampError_throwsSynchronizationException() {
        RestRequest request = signedRequest();
        when(delegate.executeRequestAndGetResponse(request))
                .thenThrow(timestampException())
                .thenThrow(timestampException());

        assertThatThrownBy(() -> restAssistant.executeRequestAndGetResponse(request))
                .isInstanceOf(RequestNotExecutedException.class)
                .hasCauseInstanceOf(ExchangeRestApiException.class);
        verify(timeSynchronizer).updateServerTimeOffset();
        verify(delegate, times(2)).executeRequestAndGetResponse(request);
    }

    @Test
    @DisplayName("재시도에서 timestamp 외 오류가 발생하면 해당 오류를 그대로 전달한다")
    void executeRequestAndGetResponse_whenRetryFailsWithDifferentError_propagatesIt() {
        RestRequest request = signedRequest();
        ExchangeRestApiException differentError = new ExchangeRestApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "{\"code\":-1000}"
        );
        when(delegate.executeRequestAndGetResponse(request))
                .thenThrow(timestampException())
                .thenThrow(differentError);

        assertThatThrownBy(() -> restAssistant.executeRequestAndGetResponse(request))
                .isSameAs(differentError);
        verify(timeSynchronizer).updateServerTimeOffset();
        verify(delegate, times(2)).executeRequestAndGetResponse(request);
    }

    private static RestRequest signedRequest() {
        return RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/v3/account")
                .authRequired(true)
                .build();
    }

    private static ExchangeRestApiException timestampException() {
        return new ExchangeTimestampException(
                new ExchangeRestApiException(
                        HttpStatus.BAD_REQUEST,
                        "{\"code\":-1021,\"msg\":\"Timestamp for this request is outside of the recvWindow.\"}"
                )
        );
    }

    private static RestResponse okResponse() {
        return new RestResponse(HttpStatus.OK, new HttpHeaders(), "{\"ok\":true}");
    }
}
