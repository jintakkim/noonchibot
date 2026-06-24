package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.core.event.Event;

record WSLiquidationEvent(
        long lid
) implements Event {
}
