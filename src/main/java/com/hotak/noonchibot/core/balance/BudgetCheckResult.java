package com.hotak.noonchibot.core.balance;


import java.math.BigDecimal;

public record BudgetCheckResult(
        boolean sufficient,
        // nullable if not calculated
        BigDecimal requiredMargin,
        // nullable if not calculated
        BigDecimal availableBalance
) {
}