package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeErrorClassifier;
import com.hotak.noonchibot.connector.ExchangeRateLimitedException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.order.ExchangeRejectedException;
import com.hotak.noonchibot.core.order.InsufficientBalanceException;
import com.hotak.noonchibot.core.order.InvalidOrderRejectedException;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

public class BinanceExchangeErrorClassifier implements ExchangeErrorClassifier {
    private static final Set<Integer> INSUFFICIENT_BALANCE_CODES = Set.of(-2010, -2019);
    private static final Set<Integer> INVALID_ORDER_CODES = Set.of(-1013, -1111, -2011, -2013, -2021, -4164);
    private static final Set<Integer> RATE_LIMIT_CODES = Set.of(-1003);
    private static final Set<Integer> TRANSIENT_CODES = Set.of(-1000, -1001, -1006, -1007, -1021);

    private final ObjectMapper objectMapper;

    public BinanceExchangeErrorClassifier() {
        this(new ObjectMapper());
    }

    public BinanceExchangeErrorClassifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public RuntimeException classify(ExchangeApiException exception) {
        if (isAlreadyClassified(exception)) {
            return exception;
        }

        HttpStatusCode statusCode = exception.httpStatusCode;
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

    private boolean isAlreadyClassified(ExchangeApiException exception) {
        return exception instanceof ExchangeTransientException;
    }

    private boolean isRateLimited(HttpStatusCode statusCode) {
        int value = statusCode.value();
        return value == 418 || value == 429;
    }

    private Integer parseCode(ExchangeApiException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getMessage());
            if (body == null || !body.has("code")) {
                return null;
            }
            return body.get("code").asInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
