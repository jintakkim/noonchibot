package com.hotak.noonchibot.strategy.bvst;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class BvstAllocationEvent {
    @Id
    private String allocationId;              // ex) "ALLOC-20260507-001"
    private String poolId;

    private BigDecimal totalUsdAllocated;     // 이 배분에서 사용된 USD

    @Enumerated(EnumType.STRING)
    private AllocationStatus status;

    private Instant scheduledAt;
    private Instant executedAt;

    // 배분 결과 캐시
    private int positionsAffected;            // 영향받은 포지션 수
    private BigDecimal longUsdAllocated;
    private BigDecimal shortUsdAllocated;

    @Convert(converter = JsonAllocationDetailConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, BigDecimal> coinAllocations;  // {BTC: 1, SHIB: 300, ...}

    public enum AllocationStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED
    }
}