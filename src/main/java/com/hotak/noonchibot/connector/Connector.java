package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.event.ExchangeEvent;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.event.EventListener;
import com.hotak.noonchibot.core.event.OrderFilledEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Connector {
    String buy(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Object... args);
    String sell(String tradingPair, BigDecimal amount, OrderType orderType, BigDecimal price, Object... args);

    /**
     * 단일 취소
     */
    void cancel(String tradingPair, String orderId);

    void cancelAll();

    List<OrderFilledEvent> getOrderFilledEvent();
    String getName();
    <T extends ExchangeEvent> void subscribe(Class<T> eventType, EventListener<T> listener);
    <T extends ExchangeEvent> void unsubscribe(Class<T> eventType, EventListener<T> listener);
    boolean isReady();

    List<String> getAllTradingPairs();
    /**
     * @return 지원하는 주문 타입 리스트
     */
    Set<OrderType> getSupportedOrderType(String tradingPair);


    //todo: amount 적절하게 quantize되는지 체크
    BigDecimal getOrderPrice(String tradingPair, boolean isBuy, BigDecimal amount);

    /**
     * 미채결 주문 반환
     */
    Map<String, InFlightOrder> getInFlightOrders();

    /**
     * 미채결 자산 반환
     */
    Map<String, BigDecimal> getInFlightAssetBalances();
    /**
     * 특정 자산의 사용 가능한 잔고를 반환한다.
     *
     * 잔고 제한(Balance Limit)이 설정된 경우, 실제 거래소 잔고와 제한 중 작은 값을 반환한다.
     * 이를 통해 봇이 사용할 수 있는 자금을 제한할 수 있다.
     *
     * 예시: 거래소에 10 ETH가 있고, 제한이 2 ETH로 설정된 경우
     * - 실제 잔고: 10 ETH
     * - 제한: 2 ETH
     * - 반환값: 2 ETH (더 작은 값)
     *
     * @param currency 자산 심볼 (예: "BTC", "USDT")
     * @return 사용 가능한 잔고
     */
    BigDecimal getAvailableBalance(String currency);


    /**
     * 체결된 주문을 기반으로 자산별 잔고 변화량을 계산한다.
     * 예시: BTC-USDT 마켓에서 1 BTC를 50,000에 매수한 경우
     * - BTC: +1
     * - USDT: -50,000
     *
     * @param startTimestamp 이 시점 이후의 체결만 계산 (null이면 전체)
     * @return 자산별 잔고 변화량 (양수: 증가, 음수: 감소)
     */
    Map<String, BigDecimal> getOrderFilledBalances(Instant startTimestamp);

    /**
     * 체결된 주문을 기반으로 최소 시점으로 부터의 자산별 잔고 변화량을 계산한다.
     * 예시: BTC-USDT 마켓에서 1 BTC를 50,000에 매수한 경우
     * - BTC: +1
     * - USDT: -50,000
     *
     * @return 자산별 잔고 변화량 (양수: 증가, 음수: 감소)
     */
    Map<String, BigDecimal> getOrderFilledBalances();


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
