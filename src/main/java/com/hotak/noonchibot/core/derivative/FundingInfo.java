package com.hotak.noonchibot.core.derivative;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Getter
public class FundingInfo {
    private final String tradingPair;
    private final String fundingCoin;
    private Duration fundingInterval;
    private BigDecimal markPrice;
    private BigDecimal fundingRate;
    private Instant nextFundingTime;

    FundingInfo(String tradingPair, String fundingCoin, Duration fundingInterval) {
        this.tradingPair = tradingPair;
        this.fundingCoin = fundingCoin;
        this.fundingInterval = fundingInterval;
    }

    /**
     * 연환산 펀딩비율
     * ex) fundingRate = 0.0001 (0.01%), interval = 8h
     */
    public BigDecimal getAnnualizedFundingRate() {
        long cyclesPerDay = Duration.ofDays(1).toSeconds() / fundingInterval.toSeconds();
        return fundingRate
                .multiply(BigDecimal.valueOf(cyclesPerDay * 365))
                .setScale(8, RoundingMode.HALF_UP);
    }

    void update(Duration fundingInterval, BigDecimal markPrice, BigDecimal fundingRate, Instant nextFundingTime) {
        if(fundingInterval != null) this.fundingInterval = fundingInterval;
        if(markPrice != null) this.markPrice = markPrice;
        if(fundingRate != null) this.fundingRate = fundingRate;
        if(nextFundingTime != null) this.nextFundingTime = nextFundingTime;
    }

    void update(Duration fundingInterval) {
        if(fundingInterval != null) this.fundingInterval = fundingInterval;
    }

    boolean isInitialized() {
        return markPrice != null && fundingRate != null && nextFundingTime != null && fundingInterval != null;
    }

}
