package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.event.internal.CoreEvent;

public record LeverageChangeIORequestedEvent(
        String tradingPair,
        int wantTo
) implements CoreEvent {}
