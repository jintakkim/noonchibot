package com.hotak.noonchibot.core.order;

import java.util.Map;
import java.util.Set;

public enum OrderState {
    /**
     * 거래소에 주문이 들어가기 전
     */
    PENDING_CREATE,
    /**
     * 거래소에 주문이 오픈된 후
     */
    OPEN,
    /**
     * 취소 대기
     */
    PENDING_CANCEL,
    /**
     * 취소되었을 때
     */
    CANCELED,
    /**
     * 일부 채결 상태(주문 자체는 살아있는 상태)
     */
    PARTIALLY_FILLED,
    /**
     * 완전 채결 상태
     */
    FILLED,
    /**
     * 실패 상태
     */
    FAILED,

    /**
     * 주문 만기시 (IOC, FOK)같은 주문은 해당 상태로 종료
     */
    EXPIRED,

    REJECTED;

    public boolean isTerminal() {
        return this == CANCELED || this == FILLED || this == FAILED || this == EXPIRED || this == REJECTED;
    }

    public boolean canTransitionTo(OrderState next) {
        if (this == next) return false;
        return ALLOWED.get(this).contains(next);
    }

    private static final Map<OrderState, Set<OrderState>> ALLOWED = Map.of(
            PENDING_CREATE,    Set.of(PENDING_CANCEL, OPEN, PARTIALLY_FILLED, FILLED, REJECTED, EXPIRED),
            OPEN,              Set.of(PENDING_CANCEL, PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED),
            PENDING_CANCEL,    Set.of(CANCELED, PARTIALLY_FILLED, FILLED),
            PARTIALLY_FILLED,  Set.of(PENDING_CANCEL, FILLED, CANCELED),

            // 종료 상태
            CANCELED, Set.of(),
            FILLED, Set.of(),
            REJECTED, Set.of(),
            EXPIRED, Set.of(),
            FAILED, Set.of()
    );
}
