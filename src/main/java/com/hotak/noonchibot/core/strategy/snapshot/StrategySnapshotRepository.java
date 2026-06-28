package com.hotak.noonchibot.core.strategy.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface StrategySnapshotRepository extends JpaRepository<StrategySnapshotHistory, String> {
    @Query("""
            select coalesce(sum(s.pnlDelta), 0)
            from StrategySnapshotHistory s
            where s.strategyId = :strategyId
            """)
    BigDecimal sumLifetimePnl(@Param("strategyId") String strategyId);

    @Query("""
            select coalesce(sum(s.pnlDelta), 0)
            from StrategySnapshotHistory s
            where s.strategyId = :strategyId
              and s.timestamp >= :since
            """)
    BigDecimal sumPnlSince(
            @Param("strategyId") String strategyId,
            @Param("since") Instant since
    );

    List<StrategySnapshotHistory> findTop100ByStrategyIdOrderByTimestampDesc(String strategyId);
}
