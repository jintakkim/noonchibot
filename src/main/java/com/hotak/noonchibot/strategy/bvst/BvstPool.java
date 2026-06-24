package com.hotak.noonchibot.strategy.bvst;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class BvstPool {
    @Id
    private String poolId;
    private String platform;                  // 거래소

    /**
     * 풀의 래버리지
     * ex) 3배이고 신규 입금자의 입금액 1000$라면 약 3000$ 어치의 포지션을 추가한다(1500$ LONG - 1500$SHORT)
     */
    private int poolLeverage;
    private BigDecimal totalShares;           // 발행된 총 share

    // 마지막 계산값 캐시
    @Transient
    private BigDecimal lastKnownNav;
    @Transient
    private Instant lastNavCalculatedAt;
    @Transient
    private BigDecimal cumulativeReturn;

    // 운용 상태
    @Enumerated(EnumType.STRING)
    private PoolStatus status;                // ACTIVE / PAUSED / WINDING_DOWN

    // 누적 통계
    private BigDecimal totalDeposited;        // 누적 입금액
    private BigDecimal totalWithdrawn;        // 누적 출금액
    private BigDecimal accumulatedTradingFees;// 누적 거래 수수료

    private Instant createdAt;
    private Instant updatedAt;

    public enum PoolStatus {
        ACTIVE,         // 정상 운용
        PAUSED,         // deposit/withdraw 일시 중단
        WINDING_DOWN    // 종료 절차
    }

    public void issueShares(BigDecimal shares, BigDecimal usdAmount) {
        this.totalShares = this.totalShares.add(shares);
        this.totalDeposited = this.totalDeposited.add(usdAmount);
    }

    public void burnShares(BigDecimal shares, BigDecimal usdAmount) {
        this.totalShares = this.totalShares.subtract(shares);
        this.totalWithdrawn = this.totalWithdrawn.add(usdAmount);
    }
}
