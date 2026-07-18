package com.hotak.noonchibot.core.exchange;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.ExchangeAuthenticationException;
import com.hotak.noonchibot.connector.ExchangeRateLimitedException;
import com.hotak.noonchibot.connector.ExchangeTransientException;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.event.internal.exchange.ExchangeFailureEvent;
import com.hotak.noonchibot.core.order.InvalidOrderRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.concurrent.CompletionException;

import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.IGNORE;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.MANUAL_BLOCK;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.RECONCILE_AND_QUARANTINE;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Action.RECORD_FAILURE;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.AUTHENTICATION;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.CONFIRMED_REJECTION;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.RATE_LIMITED;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.TRANSIENT;
import static com.hotak.noonchibot.core.exchange.FailureDecision.Kind.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultExchangeFailurePolicyTest {
    private final DefaultExchangeFailurePolicy policy = new DefaultExchangeFailurePolicy();

    @Test
    @DisplayName("인증 오류는 수동 차단으로 판단한다")
    void authenticationFailure_isManuallyBlocked() {
        Throwable cause = new ExchangeAuthenticationException(HttpStatus.UNAUTHORIZED, "invalid key");

        FailureDecision decision = policy.decide(event(ExchangeOperation.ORDER_PLACE, cause));

        assertThat(decision).isEqualTo(new FailureDecision(MANUAL_BLOCK, AUTHENTICATION));
    }

    @Test
    @DisplayName("CompletionException으로 감싼 확정 거절도 재조정 없이 종료한다")
    void wrappedConfirmedRejection_isIgnored() {
        Throwable cause = new CompletionException(
                new InvalidOrderRejectedException(new IllegalArgumentException("invalid quantity"))
        );

        FailureDecision decision = policy.decide(event(ExchangeOperation.ORDER_PLACE, cause));

        assertThat(decision).isEqualTo(new FailureDecision(IGNORE, CONFIRMED_REJECTION));
    }

    @Test
    @DisplayName("주문 등록의 일시 오류는 상태 재조정과 즉시 격리 대상으로 판단한다")
    void orderPlaceTransientFailure_isReconciledAndQuarantined() {
        Throwable cause = new ExchangeTransientException(HttpStatus.INTERNAL_SERVER_ERROR, "timeout");

        FailureDecision decision = policy.decide(event(ExchangeOperation.ORDER_PLACE, cause));

        assertThat(decision).isEqualTo(new FailureDecision(RECONCILE_AND_QUARANTINE, TRANSIENT));
    }

    @Test
    @DisplayName("주문 등록의 레이트 리밋은 상태 재조정과 즉시 격리 대상으로 판단한다")
    void orderPlaceRateLimitFailure_isReconciledAndQuarantined() {
        ExchangeApiException cause = new ExchangeApiException(HttpStatus.TOO_MANY_REQUESTS, "too many requests");

        FailureDecision decision = policy.decide(event(
                ExchangeOperation.ORDER_PLACE,
                new ExchangeRateLimitedException(cause)
        ));

        assertThat(decision).isEqualTo(new FailureDecision(RECONCILE_AND_QUARANTINE, RATE_LIMITED));
    }

    @Test
    @DisplayName("주문 등록의 미분류 오류는 결과가 불명확하므로 상태를 재조정한다")
    void orderPlaceUnknownFailure_isReconciledAndQuarantined() {
        FailureDecision decision = policy.decide(event(
                ExchangeOperation.ORDER_PLACE,
                new IllegalStateException("malformed response")
        ));

        assertThat(decision).isEqualTo(new FailureDecision(RECONCILE_AND_QUARANTINE, UNKNOWN));
    }

    @Test
    @DisplayName("주문 등록 외 작업의 일시 오류는 거래소 실패 횟수에 기록한다")
    void nonPlaceTransientFailure_isRecorded() {
        Throwable cause = new ExchangeTransientException(HttpStatus.INTERNAL_SERVER_ERROR, "timeout");

        FailureDecision decision = policy.decide(event(ExchangeOperation.ORDER_CANCEL, cause));

        assertThat(decision).isEqualTo(new FailureDecision(RECORD_FAILURE, TRANSIENT));
    }

    @Test
    @DisplayName("주문 등록 외 작업의 미분류 오류도 거래소 실패 횟수에 기록한다")
    void nonPlaceUnknownFailure_isRecorded() {
        FailureDecision decision = policy.decide(event(
                ExchangeOperation.ORDER_STATUS_QUERY,
                new IllegalStateException("unexpected")
        ));

        assertThat(decision).isEqualTo(new FailureDecision(RECORD_FAILURE, UNKNOWN));
    }

    private ExchangeFailureEvent event(ExchangeOperation operation, Throwable cause) {
        return new ExchangeFailureEvent(
                Exchange.BINANCE_DERIVATIVE,
                operation,
                "BTC-USDT",
                "client-1",
                null,
                "strategy-1",
                "group-1",
                cause,
                Instant.parse("2026-07-17T00:00:00Z")
        );
    }
}
