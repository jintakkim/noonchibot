package com.hotak.noonchibot.strategy.bvst;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class BvstDeposit {
    @Id
    @GeneratedValue
    private Long id;

    private String userId;
    private BigDecimal usdAmount;        // 입금된 금액
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    private PendingStatus status;        // PENDING / PROCESSING / PROCESSED / CANCELED

    private String strikeId;             // 어느 strike에서 처리될지

    // 처리 후
    private Instant processedAt;

    private BigDecimal sharesIssued;
    private BigDecimal entrySharePrice;

    public enum PendingStatus {
        PENDING,        // strike 대기 중
        PROCESSING,     // strike 진행 중
        PROCESSED,      // 완료 (share 발행됨)
        CANCELED,       // 유저 취소 또는 strike 실패
        REFUNDED        // 자금 환불 상태
    }
}