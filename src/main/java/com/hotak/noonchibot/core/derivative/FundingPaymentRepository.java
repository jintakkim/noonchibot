package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface FundingPaymentRepository extends JpaRepository<FundingPaymentHistory, String> {
    @Query("""
            select coalesce(sum(f.amount), 0)
            from FundingPaymentHistory f
            where f.exchange = :exchange
              and f.tradingPair = :tradingPair
              and f.positionSide = :positionSide
              and f.settledAt >= :since
            """)
    BigDecimal sumSince(
            @Param("exchange") Exchange exchange,
            @Param("tradingPair") String tradingPair,
            @Param("positionSide") PositionSide positionSide,
            @Param("since") Instant since
    );
}
