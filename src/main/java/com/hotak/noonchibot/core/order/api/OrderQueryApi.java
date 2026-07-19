package com.hotak.noonchibot.core.order.api;

import com.hotak.noonchibot.core.order.OrderView;

import java.util.Optional;

public interface OrderQueryApi {
    /**
     * 현제 inflight 상태인 주문과 최근 종료된 주문을 조회할 수 있다.
     * @return 조회 결과 없으면 null 반환
     */
    OrderView getOrderByClientOrderId(String clientOrderId);


    default Optional<OrderView> findOrderByClientOrderId(String clientOrderId) {
        return Optional.ofNullable(getOrderByClientOrderId(clientOrderId));
    }
}
