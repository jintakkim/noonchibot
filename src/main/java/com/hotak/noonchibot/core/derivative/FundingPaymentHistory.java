package com.hotak.noonchibot.core.derivative;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class FundingPaymentHistory {
    @Id
    @GeneratedValue
    private long id;

    private String tradingPair;
    private String platform;
    private Instant settledAt;
    private BigDecimal amount;
    private BigDecimal

}
