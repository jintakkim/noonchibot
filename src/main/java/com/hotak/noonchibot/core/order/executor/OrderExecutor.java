package com.hotak.noonchibot.core.order.executor;

import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OrderExecutor {
    String buy(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Object... args);
    String sell(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Object... args);
    void cancel(String tradingPair, String orderId);

    /**
     * @return 거래를 수행하는 플렛폼 명
     */
    String getPlatformName();

    /**
     * 거래가능한 모든 트레이딩 페어 반환
     */
    List<String> getAllTradingPairs();

    /**
     * @return 지원하는 주문 타입 리스트
     */
    Set<OrderType> getSupportedOrderType(String tradingPair);

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
