package com.hotak.noonchibot.connector.throttle;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Builder(toBuilder = true)
public record RateLimit(
        String limitId,
        /**
         * timeInterval당 요청 가능 개수
         */
        int limit,
        Duration timeInterval,
        /**
         * weight가 만약 1인 요청은 limit가 1씩 증가
         * weight가 만약 5인 요청은 limit가 5씩 증가
         */
        int weight,
        List<LinkedLimitWeightPair> linkedLimits
) {
    /**
     * limit 값을 조정한 새로운 RateLimit을 반환
     */
    public RateLimit withAdjustedLimit(BigDecimal percentage) {
        int adjustedLimit = Math.max(1, percentage.multiply(BigDecimal.valueOf(limit)).intValue());
        return toBuilder().limit(adjustedLimit).build();
    }

    public record LinkedLimitWeightPair(String limitId, int limit) {}
}