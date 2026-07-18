package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeAuthenticationException;
import com.hotak.noonchibot.core.order.OrderNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceExchangeErrorClassifierTest {
    private final BinanceExchangeErrorClassifier classifier =
            new BinanceExchangeErrorClassifier(new ObjectMapper());

    @Test
    @DisplayName("Binance HTTP 401 응답은 거래소 인증 오류로 분류한다")
    void unauthorized_isClassifiedAsAuthenticationFailure() {
        RuntimeException result = classifier.classify(
                new ExchangeApiException(HttpStatus.UNAUTHORIZED, "invalid key")
        );

        assertThat(result).isInstanceOf(ExchangeAuthenticationException.class);
    }

    @Test
    @DisplayName("Binance 인증 오류 코드는 HTTP 400이어도 인증 오류로 분류한다")
    void authenticationCode_isClassifiedAsAuthenticationFailure() {
        RuntimeException result = classifier.classify(
                new ExchangeApiException(HttpStatus.BAD_REQUEST, "{\"code\":-2015,\"msg\":\"Invalid API-key\"}")
        );

        assertThat(result).isInstanceOf(ExchangeAuthenticationException.class);
    }

    @Test
    @DisplayName("Binance -2013 오류는 일반 주문 거절이 아닌 주문 미존재로 분류한다")
    void orderDoesNotExistCode_isClassifiedAsOrderNotFound() {
        RuntimeException result = classifier.classify(
                new ExchangeApiException(HttpStatus.BAD_REQUEST, "{\"code\":-2013,\"msg\":\"Order does not exist\"}")
        );

        assertThat(result).isInstanceOf(OrderNotFoundException.class);
    }
}
