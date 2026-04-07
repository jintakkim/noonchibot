package com.hotak.noonchibot.core.order;

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
     * 일부 채결 상태
     */
    PARTIALLY_FILLED,
    /**
     * 완전 채결 상태
     */
    FILLED,
    /**
     * 실패 상태
     */
    FAILED;

    public boolean isTerminal() {
        return this == CANCELED || this == FILLED || this == FAILED;
    }
}
