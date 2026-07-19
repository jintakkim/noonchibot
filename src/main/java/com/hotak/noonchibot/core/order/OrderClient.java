package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.connector.web.*;

import java.util.Set;

public interface OrderClient {

    /**
     * 거래소에 신규 주문을 요청한다.
     *
     * @param order 생성할 주문
     * @return 거래소가 정상적으로 응답한 주문 접수 결과
     *
     * @throws InsufficientBalanceException
     *         주문에 필요한 잔고 또는 증거금이 부족하여 거래소가 요청을 거절한 경우
     * @throws InvalidOrderRejectedException
     *         수량, 가격, 정밀도, 최소 주문 금액 등 주문 조건이 유효하지 않아
     *         거래소가 요청을 거절한 경우
     * @throws ExchangeRateLimitedException
     *         요청 한도를 초과하여 거래소가 요청을 처리하지 않은 경우
     * @throws RequestNotExecutedException
     *         시간 동기화 실패 등의 기술적인 원인으로 거래소가 요청을 처리하지 않은 것이
     *         확인된 경우
     * @throws ExchangeRejectedException
     *         위의 구체적인 사유로 분류되지 않은 거절 응답이 발생한 경우
     * @throws ExchangeTransientException
     *         타임아웃, 응답 유실 또는 서버 오류 등으로 인해 거래소의 주문 처리 여부를
     *         확인할 수 없는 경우
     */
    OrderPlaceSuccess placeOrder(InFlightOrder order);

    /**
     * 거래소에 기존 주문의 취소를 요청한다.
     *
     * @param tradingPair 취소할 주문의 거래 페어
     * @param clientOrderId 취소할 주문의 클라이언트 주문 ID
     * @return 거래소가 정상적으로 응답한 주문 취소 결과.
     *         취소가 아직 확정되지 않은 경우에도 결과가 반환될 수 있다.
     *
     * @throws InvalidOrderRejectedException
     *         거래소에서 주문을 찾을 수 없거나 현재 주문 상태에서 취소할 수 없어
     *         거래소가 요청을 거절한 경우.
     *         주문의 현재 상태는 별도 조회를 통해 확인해야 한다.
     * @throws ExchangeRateLimitedException
     *         요청 한도를 초과하여 거래소가 요청을 처리하지 않은 경우
     * @throws RequestNotExecutedException
     *         시간 동기화 실패 등의 기술적인 원인으로 거래소가 요청을 처리하지 않은 것이
     *         확인된 경우
     * @throws ExchangeRejectedException
     *         위의 구체적인 사유로 분류되지 않은 취소 거절 응답이 발생한 경우
     * @throws ExchangeTransientException
     *         타임아웃, 응답 유실 또는 서버 오류 등으로 인해 거래소의 취소 처리 여부를
     *         확인할 수 없는 경우.
     *         주문 상태를 별도로 조회해야 한다.
     */
    OrderCancelSuccess cancelOrder(
            String tradingPair,
            String clientOrderId
    );

    /**
     * 거래소가 지원하는 주문 유효기간 조건을 반환한다.
     *
     * @return 지원하는 {@link TimeInForce} 목록
     */
    Set<TimeInForce> getSupportedTimeInForce();
}