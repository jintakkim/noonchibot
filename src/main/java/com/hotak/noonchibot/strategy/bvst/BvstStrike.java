package com.hotak.noonchibot.strategy.bvst;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class BvstStrike {
    @Id
    private String strikeId;                  // "S20260507_1800"
    private String poolId;

    private Instant cutoffAt;                 // 해당 회차의 마지막 입금 시각, 이 이후로는 이 회차에 참여 불가능
    private Instant orderScheduledAt;         // 주문 실행 시작 예정 시각
    private Instant orderExecutionStartedAt;  // 실제 주문 실행 시작 시각
    private Instant orderExecutionCompletedAt;// 실제 매수 완료 시각
    private Instant settledAt;                // share 발행 완료 시각

    @Enumerated(EnumType.STRING)
    private StrikeStatus status;

    // 측정값
    private BigDecimal navBefore;             // orderExecutionStartedAt 시점 NAV
    private BigDecimal totalSharesAtStrike;   // orderExecutionStartedAt 시점 totalShares
    private BigDecimal sharePriceAtStrike;    // navBefore / totalSharesAtStrike

    // 실행 결과 집계
    private BigDecimal totalDepositCash;      // pending deposit 합
    private BigDecimal totalWithdrawShares;   // pending withdraw share 합

    /*
        orderExecutionStartedAt 시점 deposit cash - withdraw cash(sharePriceAtStrike * totalWithdrawShares)
        실제 거래 수수료는 반영되지 않음
     */
    private BigDecimal netCashFlow;
    /*
        실제 매수에 사용된 cash
        해당 시점 출금금액이 매수 금액보다 큰경우 -값으로 처리된다(cash inflow)
     */
    private BigDecimal totalCashSpent;
    // 발행/소각 결과 - 출금 금액인 매수 금액 보다 큰경우 -값으로 처리된다
    private BigDecimal totalSharesIssued;

    private int depositsProcessed;
    private int withdrawsProcessed;

    public enum StrikeStatus {
        PENDING,            // 예정됨, 대기
        CUTOFF_PASSED,      // cutoff 지나서 quiet window
        EXECUTING,          // 매수/매도 진행 중
        SETTLING,           // 발행/소각 처리
        COMPLETED,          // 정상 완료
        FAILED              // 실패 (자금 환불 필요)
        }

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC);

    protected BvstStrike(
            String strikeId,
            String poolId,
            Instant cutoffAt,
            Instant orderScheduledAt
            ) {
        this.strikeId = strikeId;
        this.poolId = poolId;
        this.cutoffAt = cutoffAt;
        this.orderScheduledAt = orderScheduledAt;
        this.status = StrikeStatus.PENDING;
        this.totalDepositCash = BigDecimal.ZERO;
        this.totalWithdrawShares = BigDecimal.ZERO;
        this.depositsProcessed = 0;
        this.withdrawsProcessed = 0;
    }

    public void recordNavBefore(BigDecimal nav, BigDecimal totalShares) {
        this.navBefore = nav;
        this.totalSharesAtStrike = totalShares;
        this.sharePriceAtStrike = totalShares.signum() == 0
                ? BigDecimal.ONE
                : nav.divide(totalShares, 10, RoundingMode.HALF_UP);
    }

    public static BvstStrike create(
            String poolId,
            Instant cutoffAt,
            Instant orderScheduledAt
    ) {

        return new BvstStrike(generateId(cutoffAt), poolId, cutoffAt, orderScheduledAt);
    }

    private static String generateId(Instant cutoffAt) {
        return "STRIKE" + FORMAT.format(cutoffAt);
    }

}