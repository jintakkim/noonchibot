package com.hotak.noonchibot.core.strategy.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_strategy_snapshot_strategy_time", columnList = "strategyId,timestamp")
})
public class StrategySnapshotHistory {
    @Id
    private String id;

    @Column(nullable = false)
    private String strategyId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal pnl;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal pnlDelta;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal lifetimePnl;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal dailyPnl;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal fourHourPnl;

    @Lob
    private String metrics;
}
