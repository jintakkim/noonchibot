package com.hotak.noonchibot.core.strategy.arbitrage;

public enum UnbalancedLegHandling {
    WAIT_FOR_OTHER_LEG,
    AGGRESSIVELY_COMPLETE_OTHER_LEG,
    CANCEL_AND_REDUCE_FILLED_LEG
}
