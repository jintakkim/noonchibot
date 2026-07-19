package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.web.ExchangeRejectedException;
import com.hotak.noonchibot.connector.web.InsufficientBalanceException;
import com.hotak.noonchibot.core.order.InvalidOrderRejectedException;
import com.hotak.noonchibot.core.utils.AsyncUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@RequiredArgsConstructor
public class BinanceExchangeErrorClassifier implements ExchangeErrorClassifier {
    private static final Set<Integer> INSUFFICIENT_BALANCE_CODES = Set.of(-2010, -2019);
    private static final Set<Integer> INVALID_ORDER_CODES = Set.of(-1013, -1111, -2011, -2013, -2021, -4164);
    private static final Set<Integer> NO_CHANGE_REQUIRED_CODES = Set.of(-4059, -4046);
    private static final Set<Integer> RATE_LIMIT_CODES = Set.of(-1003);
    private static final Set<Integer> TRANSIENT_CODES = Set.of(-1000, -1001, -1006, -1007);
    private static final int TIMESTAMP_ERROR_CODE = -1021;

    private final ObjectMapper objectMapper;

    @Override
    public ExchangeRestApiException classify(ExchangeRestApiException exception) {
        HttpStatusCode statusCode = exception.statusCode();
        if (isRateLimited(statusCode)) {
            return new ExchangeRateLimitedException(exception);
        }
        if (statusCode.is5xxServerError()) {
            return new ExchangeTransientException(exception);
        }
        Integer code = parseCode(exception);
        if (code == null) {
            return statusCode.is4xxClientError()
                    ? new ExchangeRejectedException(exception)
                    : new ExchangeTransientException(exception);
        }
        if (INSUFFICIENT_BALANCE_CODES.contains(code)) {
            return new InsufficientBalanceException(exception);
        }
        if (INVALID_ORDER_CODES.contains(code)) {
            return new InvalidOrderRejectedException(exception);
        }
        if (NO_CHANGE_REQUIRED_CODES.contains(code)) {
            return new NoChangeRequiredException(exception);
        }
        if (code == TIMESTAMP_ERROR_CODE) {
            return new ExchangeTimestampException(exception);
        }
        if (RATE_LIMIT_CODES.contains(code)) {
            return new ExchangeRateLimitedException(exception);
        }
        if (TRANSIENT_CODES.contains(code)) {
            return new ExchangeTransientException(exception);
        }
        if (statusCode.is4xxClientError()) {
            return new ExchangeRejectedException(exception);
        }
        return new ExchangeTransientException(exception);
    }

    /**
     * 만약 true라면 retry한다.
     */
    @Override
    public boolean test(Throwable throwable) {
        return AsyncUtils.unwrapCompletionException(throwable) instanceof ExchangeTransientException;
    }

    private boolean isRateLimited(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 418 || value == 429;
    }

    private Integer parseCode(ExchangeRestApiException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.responseBody());
            if (body == null || !body.has("code")) {
                return null;
            }
            return body.get("code").asInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
