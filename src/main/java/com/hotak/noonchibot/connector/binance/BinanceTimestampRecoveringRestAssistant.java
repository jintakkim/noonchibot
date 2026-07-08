package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.RestResponse;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletionException;

/**
 * throwError=true로 ExchangeApiException이 나온 경우와 throwError=false로 RestResponse가 나온 경우 둘 다 처리
 */
@RequiredArgsConstructor
public class BinanceTimestampRecoveringRestAssistant implements RestAssistant {
    private static final int TIMESTAMP_ERROR_CODE = -1021;

    private final RestAssistant delegate;
    private final TimeSynchronizer timeSynchronizer;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode executeRequestAndGetJsonBody(RestRequest request) {
        return objectMapper.readTree(executeRequestAndGetResponse(request).body());
    }

    @Override
    public RestResponse executeRequestAndGetResponse(RestRequest request) {
        try {
            RestResponse response = delegate.executeRequestAndGetResponse(request);
            if (!isRecoverableTimestampError(request, response)) {
                return response;
            }
            timeSynchronizer.updateServerTimeOffset();
            return delegate.executeRequestAndGetResponse(request);
        } catch (ExchangeApiException e) {
            if (!isRecoverableTimestampError(request, e)) {
                throw e;
            }
            timeSynchronizer.updateServerTimeOffset();
            return delegate.executeRequestAndGetResponse(request);
        } catch (CompletionException e) {
            ExchangeApiException exchangeApiException = unwrapExchangeApiException(e);
            if (exchangeApiException == null || !isRecoverableTimestampError(request, exchangeApiException)) {
                throw e;
            }
            timeSynchronizer.updateServerTimeOffset();
            return delegate.executeRequestAndGetResponse(request);
        }
    }

    private boolean isRecoverableTimestampError(RestRequest request, RestResponse response) {
        return request.authRequired()
                && response.statusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                && hasTimestampErrorCode(response.body());
    }

    private boolean isRecoverableTimestampError(RestRequest request, ExchangeApiException e) {
        return request.authRequired()
                && e.httpStatusCode.isSameCodeAs(HttpStatus.BAD_REQUEST)
                && hasTimestampErrorCode(e.getMessage());
    }

    private boolean hasTimestampErrorCode(String body) {
        try {
            return objectMapper.readTree(body).path("code").asInt() == TIMESTAMP_ERROR_CODE;
        } catch (Exception e) {
            return false;
        }
    }

    private ExchangeApiException unwrapExchangeApiException(CompletionException e) {
        Throwable cause = e.getCause();
        return cause instanceof ExchangeApiException exchangeApiException ? exchangeApiException : null;
    }
}
