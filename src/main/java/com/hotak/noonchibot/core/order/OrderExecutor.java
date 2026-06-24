package com.hotak.noonchibot.core.order;

import java.math.BigDecimal;
import java.util.List;

public interface OrderExecutor {
    /**
     * async 하게 동작, 리턴되는 클라이언트 오더 아이디로써의 조회는 즉시 보장된다.
     * @return clientOrderId
     */
    String buy(OrderCandidate candidate);
    /**
     * async 하게 동작, 리턴되는 클라이언트 오더 아이디로써의 조회는 즉시 보장된다.
     * @return clientOrderId
     */
    String sell(OrderCandidate candidate);
    /**
     * async 하게 동작, 취소를 보장하지는 않는다.
     */
    void cancel(String tradingPair, String clientOrderId);

    /**
     * @return 거래를 수행하는 플렛폼 명
     */
    String getPlatformName();

    /**
     * 거래가능한 모든 트레이딩 페어 반환
     */
    List<String> getAllTradingPairs();


    /**
     * 가격 최소 단위 (tick size)
     */
    BigDecimal getOrderPriceQuantum(String tradingPair, BigDecimal amount);

    /**
     * 수량 최소 단위 (lot size)
     */
    BigDecimal getOrderSizeQuantum(String tradingPair, BigDecimal amount);

    /**
     * 거래소 규칙에 맞게 주문 가격을 양자화(quantize)한다.
     * 예시: price=100.123, quantum=0.01 → 100.12
     *
     * @param tradingPair 거래쌍
     * @param price 원래 가격
     * @return 양자화된 가격
     */
    BigDecimal quantizeOrderPrice(String tradingPair, BigDecimal price);

    /**
     * 거래소 규칙에 맞게 주문 수량을 양자화(quantize)한다.
     *
     * @param tradingPair 거래쌍
     * @param amount 원래 수량
     * @return 양자화된 수량
     */
    BigDecimal quantizeOrderAmount(String tradingPair, BigDecimal amount);
}
