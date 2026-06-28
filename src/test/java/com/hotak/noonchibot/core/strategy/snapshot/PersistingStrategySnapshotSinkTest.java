package com.hotak.noonchibot.core.strategy.snapshot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistingStrategySnapshotSinkTest {
    @Test
    @DisplayName("snapshot 저장 시 lifetime/daily/4h pnl을 계산해 저장한다")
    void publish_savesSnapshotWithWindowPnl() {
        StrategySnapshotRepository repository = mock(StrategySnapshotRepository.class);
        Instant now = Instant.parse("2026-06-01T12:00:00Z");
        when(repository.sumLifetimePnl("funding-arb")).thenReturn(new BigDecimal("10"));
        when(repository.sumPnlSince("funding-arb", now.minus(java.time.Duration.ofHours(24))))
                .thenReturn(new BigDecimal("3"));
        when(repository.sumPnlSince("funding-arb", now.minus(java.time.Duration.ofHours(4))))
                .thenReturn(new BigDecimal("1"));
        PersistingStrategySnapshotSink sink = new PersistingStrategySnapshotSink(repository);

        sink.publish(new StrategySnapshot(
                "funding-arb",
                now,
                "FUNDING_ARBITRAGE",
                new BigDecimal("15"),
                new BigDecimal("0.5"),
                Map.of("targetBaseAmount", new BigDecimal("1.0"))
        ));

        ArgumentCaptor<StrategySnapshotHistory> captor = ArgumentCaptor.forClass(StrategySnapshotHistory.class);
        verify(repository).save(captor.capture());
        StrategySnapshotHistory saved = captor.getValue();
        assertThat(saved.getStrategyId()).isEqualTo("funding-arb");
        assertThat(saved.getPnl()).isEqualByComparingTo("15");
        assertThat(saved.getPnlDelta()).isEqualByComparingTo("0.5");
        assertThat(saved.getLifetimePnl()).isEqualByComparingTo("10.5");
        assertThat(saved.getDailyPnl()).isEqualByComparingTo("3.5");
        assertThat(saved.getFourHourPnl()).isEqualByComparingTo("1.5");
        assertThat(saved.getMetrics()).contains("targetBaseAmount");
    }
}
