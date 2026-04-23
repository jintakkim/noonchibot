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
    FOK,
    /**
     * 즉시 체결이 되는 상황이라면 주문을 넣지 않음 (메이커로만 주문)
     * 업비트는 POST_ONLY가 TimeInForce 값으로 들어간다
     */
    POST_ONLY
}
