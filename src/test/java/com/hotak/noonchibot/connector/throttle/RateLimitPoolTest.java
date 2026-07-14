package com.hotak.noonchibot.connector.throttle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitPool 용량 계산")
class RateLimitPoolTest {
    private static final double FIVE_PERCENT_MARGIN = 0.05;

    private final TestClock clock = new TestClock(Instant.EPOCH);
    private final RateLimitPool pool = new RateLimitPool(
            RateLimit.pool("POOL", 100, Duration.ofSeconds(100)),
            clock
    );

    @Test
    @DisplayName("safety margin은 시간 구간이 아니라 허용량에서 차감한다")
    void safetyMarginReducesEffectiveLimit() {
        assertThat(pool.hasCapacity(95, FIVE_PERCENT_MARGIN)).isTrue();
        assertThat(pool.hasCapacity(96, FIVE_PERCENT_MARGIN)).isFalse();
    }

    @Test
    @DisplayName("기록은 전체 시간 구간이 지난 뒤 정확히 만료된다")
    void recordedUsageExpiresAfterFullWindow() {
        pool.record(95);

        clock.advance(Duration.ofMillis(95_001));
        assertThat(pool.hasCapacity(1, FIVE_PERCENT_MARGIN)).isFalse();

        clock.advance(Duration.ofMillis(4_999));
        assertThat(pool.hasCapacity(95, FIVE_PERCENT_MARGIN)).isTrue();
    }
}
