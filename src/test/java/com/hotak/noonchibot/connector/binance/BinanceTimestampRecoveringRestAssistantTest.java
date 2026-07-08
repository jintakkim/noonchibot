package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.RestResponse;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletionException;

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
    private BinanceTimestampRecoveringRestAssistant restAssistant;

    @BeforeEach
    void setUp() {
        delegate = mock(RestAssistant.class);
        timeSynchronizer = mock(TimeSynchronizer.class);
        restAssistant = new BinanceTimestampRecoveringRestAssistant(
                delegate,
                timeSynchronizer,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("인증 요청에서 timestamp 오류 예외가 발생하면 서버 시간을 갱신하고 한 번 재시도한다")
    void executeRequestAndGetResponse_whenTimestampException_refreshesServerTimeAndRetries() {
        RestRequest request = signedRequest(true);
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
    @DisplayName("비동기 실행 중 timestamp 오류 예외가 CompletionException으로 감싸져도 서버 시간을 갱신하고 한 번 재시도한다")
    void executeRequestAndGetResponse_whenTimestampExceptionWrappedInCompletionException_refreshesServerTimeAndRetries() {
        RestRequest request = signedRequest(true);
        RestResponse success = okResponse();
        when(delegate.executeRequestAndGetResponse(request))
                .thenThrow(new CompletionException(timestampException()))
                .thenReturn(success);

        RestResponse response = restAssistant.executeRequestAndGetResponse(request);

        assertThat(response).isSameAs(success);
        verify(timeSynchronizer).updateServerTimeOffset();
        verify(delegate, times(2)).executeRequestAndGetResponse(request);
    }

    @Test
    @DisplayName("throwError=false 요청에서 timestamp 오류 응답이 오면 서버 시간을 갱신하고 한 번 재시도한다")
    void executeRequestAndGetResponse_whenTimestampResponse_refreshesServerTimeAndRetries() {
        RestRequest request = signedRequest(false);
        RestResponse success = okResponse();
        when(delegate.executeRequestAndGetResponse(request))
                .thenReturn(timestampResponse())
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
        ExchangeApiException exception = timestampException();
        when(delegate.executeRequestAndGetResponse(request)).thenThrow(exception);

        assertThatThrownBy(() -> restAssistant.executeRequestAndGetResponse(request))
                .isSameAs(exception);
        verify(timeSynchronizer, never()).updateServerTimeOffset();
        verify(delegate).executeRequestAndGetResponse(request);
    }

    @Test
    @DisplayName("JSON body 요청도 공통 응답 retry 경로를 탄다")
    void executeRequestAndGetJsonBody_usesRecoveringResponsePath() {
        RestRequest request = signedRequest(true);
        when(delegate.executeRequestAndGetResponse(request))
                .thenThrow(timestampException())
                .thenReturn(new RestResponse(HttpStatus.OK, new HttpHeaders(), "{\"ok\":true}"));

        assertThat(restAssistant.executeRequestAndGetJsonBody(request).get("ok").asBoolean()).isTrue();
        verify(timeSynchronizer).updateServerTimeOffset();
        verify(delegate, times(2)).executeRequestAndGetResponse(request);
        verify(delegate, never()).executeRequestAndGetJsonBody(request);
    }

    private static RestRequest signedRequest(boolean throwError) {
        return RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl("/v3/account")
                .authRequired(true)
                .throwError(throwError)
                .build();
    }

    private static ExchangeApiException timestampException() {
        return new ExchangeApiException(
                HttpStatus.BAD_REQUEST,
                "{\"code\":-1021,\"msg\":\"Timestamp for this request is outside of the recvWindow.\"}"
        );
    }

    private static RestResponse timestampResponse() {
        return new RestResponse(
                HttpStatus.BAD_REQUEST,
                new HttpHeaders(),
                "{\"code\":-1021,\"msg\":\"Timestamp for this request is outside of the recvWindow.\"}"
        );
    }

    private static RestResponse okResponse() {
        return new RestResponse(HttpStatus.OK, new HttpHeaders(), "{\"ok\":true}");
    }
}
