package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.derivative.MarginMode;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

public record MarginModeChangeIORequestedEvent(
        String tradingPair,
        MarginMode wantTo
) implements CoreEvent {}
