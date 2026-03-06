package com.hotak.noonchibot.core.trade.fee;

import java.math.BigDecimal;

public record TokenAmount(String token, BigDecimal amount) {
    public TokenAmount {
        if (amount == null) amount = BigDecimal.ZERO;
    }
}