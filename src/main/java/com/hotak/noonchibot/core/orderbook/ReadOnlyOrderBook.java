package com.hotak.noonchibot.core.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ReadOnlyOrderBook {
    /**
     * @return 가격 기준 내림차순 정렬된 entries 반환(best price 부터 반환)
     */
    List<OrderBookEntry> getBidEntries();
    /**
     * @return 가격 기준 오름차순 정렬된 entries 반환(best price 부터 반환)
     */
    List<OrderBookEntry> getAskEntries();
    Long getSnapshotId();
    Long getLastDiffId();
    BigDecimal getBestBid();
    BigDecimal getBestAsk();
    BigDecimal getLastTradePrice();

    /**
     * 주문 체결 시 적용될 최우선 가격을 반환한다.
     *
     * @param isBuy true: 매수 시 체결가(best ask), false: 매도 시 체결가(best bid)
     * @return 최우선 호가, 호가가 없다면 null을 리턴
     */
    BigDecimal getBestPrice(boolean isBuy);

    /**
     * 지정된 기초자산 수량을 체결하기 위해 도달해야 하는 가격을 반환한다.
     *
     * <p>예시: BTC/USDT 마켓에서 10 BTC 매수 시 어느 가격까지 도달하는지 계산
     *
     * @param isBuy true: 매수(ask 방향 탐색), false: 매도(bid 방향 탐색)
     * @param volume 체결하려는 기초자산 수량 (예: BTC)
     * @return 체결 결과 (도달 가격, 체결 가능 수량, 충족 여부)
     */
    OrderBookQueryResult getImpactPriceForBaseVolume(boolean isBuy, BigDecimal volume);

    /**
     * 지정된 수량을 체결할 때 예상되는 VWAP(거래량 가중 평균가)를 계산한다.
     *
     * @param isBuy true: 매수(ask 방향), false: 매도(bid 방향)
     * @param volume 체결하려는 수량
     * @return VWAP 계산 결과
     */
    OrderBookQueryResult getVWAPForVolume(boolean isBuy, BigDecimal volume);

    /**
     * 지정된 금액(quote volume)을 체결하기 위해 도달해야 하는 가격을 반환한다.
     *
     * @param isBuy true: 매수(ask 방향), false: 매도(bid 방향)
     * @param quoteVolume 체결하려는 금액 (예: USDT)
     * @return 해당 금액을 채우기 위한 최종 가격
     */
    OrderBookQueryResult getImpactPriceForQuoteVolume(boolean isBuy, BigDecimal quoteVolume);

    /**
     * 지정된 기초자산 수량을 체결하기 위해 필요한 Quote 금액을 계산한다.
     * 예시: BTC/USDT 마켓에서 10 BTC 매수 시 필요한 USDT 금액 계산
     *
     * @param isBuy true: 매수(ask 방향), false: 매도(bid 방향)
     * @param baseVolume 체결하려는 기초자산 수량 (예: BTC)
     * @return 필요한 Quote 금액 (resultVolume에 담김)
     */
    OrderBookQueryResult getQuoteVolumeForBaseVolume(boolean isBuy, BigDecimal baseVolume);

    /**
     * 지정된 가격 범위 내에서 체결 가능한 기초자산 수량을 계산한다.
     * 예시: BTC/USDT 마켓에서 102 이하 가격으로 매수 가능한 BTC 수량 계산
     *
     * @param isBuy true: 매수(price 이하의 ask), false: 매도(price 이상의 bid)
     * @param price 가격 제한
     * @return 체결 가능 수량 (resultVolume에 담김), resultPrice는 마지막 채결가로 설정된다.
     */
    OrderBookQueryResult getVolumeForPrice(boolean isBuy, BigDecimal price);

    /**
     * 지정된 가격 범위 내에서 체결 시 필요한 Quote 금액을 계산한다.
     *
     * <p>예시: BTC/USDT 마켓에서 102 이하로 매수 시 필요한 USDT 금액 계산
     *
     * @param isBuy true: 매수(price 이하의 ask), false: 매도(price 이상의 bid)
     * @param price 가격 제한
     * @return Quote 금액 (resultVolume에 담김)
     */
    OrderBookQueryResult getQuoteVolumeForPrice(boolean isBuy, BigDecimal price);

    /**
     * 마지막으로 Trade가 적용된 시간을 반환
     */
    Instant getLastAppliedTradeTime();
}
