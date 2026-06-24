package com.hotak.noonchibot.core.order;

import java.time.Instant;

/**
 *
 * @param exchangeOrderId nullable -> 주문이 비동기적으로 생성, 혹은 타임아웃으로 주문생성 여부를 모를떄
 * @param orderState exchangeOrderId가 null일때는 PENDING_CREATE로 전달된다.
 * @param timestamp
 */
public record OrderPlaceResult(
        String exchangeOrderId,
        OrderState orderState,
        Instant timestamp
) {
}
