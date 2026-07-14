package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.throttle.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Binance USD-M 요청 제한 설정")
class ApiSpecRateLimitTest {
    @Test
    @DisplayName("주문 제한 풀은 1분 1200건과 10초 300건으로 설정한다")
    void orderPoolsMatchExchangeLimits() {
        RateLimit oneMinute = rateLimit("ORDERS_1MIN");
        RateLimit tenSeconds = rateLimit("ORDERS_10SEC");

        assertThat(oneMinute.limit()).isEqualTo(1200);
        assertThat(oneMinute.timeInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(tenSeconds.limit()).isEqualTo(300);
        assertThat(tenSeconds.timeInterval()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("주문 엔드포인트는 request weight와 두 주문 제한 풀을 함께 소비한다")
    void orderEndpointUsesAllOrderPools() {
        RateLimit orderEndpoint = rateLimit(ApiSpec.ORDER_PATH_URL);

        assertThat(orderEndpoint.linkedLimits()).containsExactly(
                new RateLimit.LinkedLimitWeightPair("REQUEST_WEIGHT", 1),
                new RateLimit.LinkedLimitWeightPair("ORDERS_1MIN", 1),
                new RateLimit.LinkedLimitWeightPair("ORDERS_10SEC", 1)
        );
    }

    @Test
    @DisplayName("모든 linked limit ID는 RATE_LIMITS에 선언되어 있다")
    void everyLinkedLimitIdIsDeclared() {
        Set<String> declaredIds = ApiSpec.RATE_LIMITS.stream()
                .map(RateLimit::limitId)
                .collect(Collectors.toSet());

        assertThat(ApiSpec.RATE_LIMITS.stream()
                .flatMap(rateLimit -> rateLimit.linkedLimits().stream())
                .map(RateLimit.LinkedLimitWeightPair::limitId)
                .filter(linkedId -> !declaredIds.contains(linkedId))
                .toList())
                .isEmpty();
    }

    private static RateLimit rateLimit(String limitId) {
        return ApiSpec.RATE_LIMITS.stream()
                .filter(rateLimit -> rateLimit.limitId().equals(limitId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing rate limit: " + limitId));
    }
}
