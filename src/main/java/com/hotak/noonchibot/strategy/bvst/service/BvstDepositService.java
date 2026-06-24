package com.hotak.noonchibot.strategy.bvst.service;

import java.math.BigDecimal;

public interface BvstDepositService {
    void deposit(long userId, BigDecimal amount);
}
