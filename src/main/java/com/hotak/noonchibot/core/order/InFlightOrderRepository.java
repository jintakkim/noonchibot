package com.hotak.noonchibot.core.order;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 현제 진행중인 오더에 대한 리포지토리
 * 종료된 주문은 historical
 */
public interface InFlightOrderRepository {

    void save(InFlightOrder inFlightOrder);

    void deleteByClientId(String clientOrderId);

    /**
     * inFlightOrder 필드 수정시 반드시 이 메서드를 통해 업데이트, 직접 수정시 스레드 세이프하지 않다.
     */
    void update(String clientOrderId, Consumer<InFlightOrder> action);

    /**
     * clientOrderId로 먼저 탐색 clientOrderId가 null이라면 exchangeOrderId로 탐색
     */
    Optional<InFlightOrder> findById(String clientOrderId, String exchangeOrderId);
    Optional<InFlightOrder> findByClientId(String clientOrderId);
    Optional<InFlightOrder> findByExchangeId(String clientOrderId);

}
