package com.hotak.noonchibot.core.order;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class OrderHistory {
    @Id
    private String clientOrderId;



}
