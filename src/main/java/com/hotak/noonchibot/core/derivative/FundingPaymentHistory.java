package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.core.Exchange;
import jakarta.persistence.*;
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
        @Index(
                name = "idx_funding_payment_position_time",
                columnList = "exchange,tradingPair,positionSide,settledAt"
        )
})
public class FundingPaymentHistory {
    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Exchange exchange;

    @Column(nullable = false)
    private String tradingPair;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionSide positionSide;

    @Column(nullable = false, precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false)
    private String asset;

    @Column(nullable = false)
    private Instant settledAt;

    static FundingPaymentHistory from(FundingPayment payment) {
        return new FundingPaymentHistory(
                payment.id(),
                payment.exchange(),
                payment.tradingPair(),
                payment.positionSide(),
                payment.amount(),
                payment.asset(),
                payment.timestamp()
        );
    }
}
