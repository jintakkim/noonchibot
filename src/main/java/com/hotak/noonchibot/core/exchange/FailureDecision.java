package com.hotak.noonchibot.core.exchange;

import java.util.Objects;

/**
 * 거래소 요청 실패를 분류한 뒤 수행할 후속 작업을 표현한다.
 */
public record FailureDecision(Action action, Kind kind) {
    public FailureDecision {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(kind, "kind");
    }

    public enum Action {
        IGNORE,
        RECORD_FAILURE,
        RECONCILE_AND_QUARANTINE,
        MANUAL_BLOCK
    }

    public enum Kind {
        AUTHENTICATION,
        CONFIRMED_REJECTION,
        RATE_LIMITED,
        TRANSIENT,
        UNKNOWN
    }
}
