package com.hotak.noonchibot.core.order.api;

import com.hotak.noonchibot.core.order.OrderCandidate;
import com.hotak.noonchibot.core.order.OrderValidationException.*;

public interface OrderCommandApi {
    /**
     * 주문을 생성한다.
     * non-blocking method이다, 주문 생성 결과 조회를 위해서는 주문 읽기 api를 사용한다.
     *
     * @return clientOrderId 주문에 대한 Id이다.
     * @throws BelowMinOrderSizeException 최소 주문 크기보다 주문량이 작을떄
     * @throws UnsupportedOrderTypeException 지원하지 않는 오더 타입일때
     * @throws UnsupportedTimeInForceException 지원하지 않는 timeInForce 일때
     * @throws BelowMinNotionalException 최소 주문 notional 수량보다 주문량이 작을때
     */
    String createOrder(OrderCandidate candidate);

    /**
     * 주문을 취소한다
     * non-blocking method이다, 주문 취소 결과 조회를 위해서는 주문 읽기 api를 사용한다.
     *
     * @throws IllegalArgumentException 존재하지 않는 clientOrderId 일때
     */
    void placeCancel(String clientOrderId);


}
