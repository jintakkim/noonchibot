package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class OrderSnapshot {
    @Id
    private String clientOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Exchange exchange;
    @Column(nullable = false)
    private String tradingPair;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderState state;
    private String exchangeOrderId;
    private Instant createdAt;
    private Instant updatedAt;
}
