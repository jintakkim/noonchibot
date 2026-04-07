package com.hotak.noonchibot.core.order;

public enum TimeInForce {
    /**
     * 캔슬 전까지 유효
     */
    GTC,
    /**
     * 주문 직후 남은 물량은 즉시 취소
     */
    IOC,
    /**
     * 전량 채결아니면 취소
     */
    FOK
}
