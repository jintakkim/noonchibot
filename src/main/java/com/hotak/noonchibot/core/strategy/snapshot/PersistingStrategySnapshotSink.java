package com.hotak.noonchibot.core.strategy.snapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class PersistingStrategySnapshotSink implements StrategySnapshotSink {
    private static final Duration DAILY_WINDOW = Duration.ofHours(24);
    private static final Duration FOUR_HOUR_WINDOW = Duration.ofHours(4);

    private final StrategySnapshotRepository strategySnapshotRepository;

    @Override
    public void publish(StrategySnapshot snapshot) {
        BigDecimal previousLifetime = strategySnapshotRepository.sumLifetimePnl(snapshot.strategyId());
        BigDecimal previousDaily = strategySnapshotRepository.sumPnlSince(
                snapshot.strategyId(),
                snapshot.timestamp().minus(DAILY_WINDOW)
        );
        BigDecimal previousFourHour = strategySnapshotRepository.sumPnlSince(
                snapshot.strategyId(),
                snapshot.timestamp().minus(FOUR_HOUR_WINDOW)
        );

        StrategySnapshotHistory history = new StrategySnapshotHistory(
                UUID.randomUUID().toString(),
                snapshot.strategyId(),
                snapshot.timestamp(),
                snapshot.type(),
                snapshot.pnl(),
                snapshot.pnlDelta(),
                previousLifetime.add(snapshot.pnlDelta()),
                previousDaily.add(snapshot.pnlDelta()),
                previousFourHour.add(snapshot.pnlDelta()),
                snapshot.metrics().toString()
        );
        strategySnapshotRepository.save(history);
    }
}
