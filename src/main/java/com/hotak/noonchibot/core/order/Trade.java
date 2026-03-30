package com.hotak.noonchibot.core.order;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {
    @Id
    private String tradeId;
    private String clientOrderId;
    private String exchangeOrderId;
    private Instant fillTimestamp;
    private BigDecimal fillPrice;
    private BigDecimal fillBaseAmount;
    private BigDecimal fillQuoteAmount;
    private String feeToken;
    private BigDecimal feeAmount;
    private Boolean isMake;
}
