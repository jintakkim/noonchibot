package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.event.internal.CoreEvent;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import com.hotak.noonchibot.core.derivative.funding.FundingRatePoint;

public sealed interface FundingInfoEvent extends CoreEvent {
    /**
     * 서버으로 들어온 funding info 정보
     * 거래소 마다 응답 payload가 다르기 때문에 fundingInterval 값이 null일 수 있다.
     * fundingInterval이 포함 안되는 거래소는 IntervalRestFetch로 별도로 조회해야한다.
     * */
    record Received(
            String tradingPair,
            Instant eventTime,
            BigDecimal markPrice,
            BigDecimal fundingRate,
            Instant nextFundingTime,
            Duration fundingInterval
    ) implements FundingInfoEvent {}

    /** REST로 즉시 조회 요청 */
    record RestFetchRequested(String tradingPair) implements FundingInfoEvent {}

    /** REST 조회 실패 */
    record RestFetchFailed(Throwable cause) implements FundingInfoEvent {}

    /**
     * Funding interval 조회 요청(일부 거래소에서는 미지원, 기본 조회 요청에 포함)
     */
    record IntervalRestFetchRequested() implements FundingInfoEvent {}

    /**
     * Funding interval 조회
     * */
    record IntervalReceived(Map<String, Duration> snapshot) implements FundingInfoEvent {}

    /** Funding interval 조회 실패 */
    record IntervalRestFetchFailed(Throwable cause) implements FundingInfoEvent {}

    record HistoryFetchRequested(
            String tradingPair,
            Instant fundingTime,
            Duration fundingInterval,
            int attempt
    ) implements FundingInfoEvent {}

    record HistoryReceived(
            String tradingPair,
            Instant fundingTime,
            Duration fundingInterval,
            int attempt,
            List<FundingRatePoint> points
    ) implements FundingInfoEvent {
        public HistoryReceived {
            points = List.copyOf(points);
        }
    }

    record HistoryFetchFailed(
            String tradingPair,
            Instant fundingTime,
            Duration fundingInterval,
            int attempt,
            Throwable cause
    ) implements FundingInfoEvent {}

    record HistoryUpdated(String tradingPair) implements FundingInfoEvent {}
}
