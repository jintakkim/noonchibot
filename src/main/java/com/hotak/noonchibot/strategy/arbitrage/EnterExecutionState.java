package com.hotak.noonchibot.strategy.arbitrage;

public enum EnterExecutionState {
    GAP_DETECTION,
    GENERATE_BOTH_TAKER_ORDER,
    LEG_RISK_CONTROL,
    GENERATE_CORRECTION_ORDER,
    EXECUTION_EVALUATION
}
