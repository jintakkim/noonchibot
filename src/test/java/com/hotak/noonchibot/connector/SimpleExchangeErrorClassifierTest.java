package com.hotak.noonchibot.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleExchangeErrorClassifierTest {
    private final SimpleExchangeErrorClassifier classifier = new SimpleExchangeErrorClassifier();

    @Test
    @DisplayName("HTTP 401 응답은 거래소 인증 오류로 분류한다")
    void unauthorized_isClassifiedAsAuthenticationFailure() {
        RuntimeException result = classifier.classify(
                new ExchangeApiException(HttpStatus.UNAUTHORIZED, "invalid key")
        );

        assertThat(result).isInstanceOf(ExchangeAuthenticationException.class);
    }

    @Test
    @DisplayName("HTTP 403 응답은 거래소 권한 오류를 포함한 인증 오류로 분류한다")
    void forbidden_isClassifiedAsAuthenticationFailure() {
        RuntimeException result = classifier.classify(
                new ExchangeApiException(HttpStatus.FORBIDDEN, "permission denied")
        );

        assertThat(result).isInstanceOf(ExchangeAuthenticationException.class);
    }
}
