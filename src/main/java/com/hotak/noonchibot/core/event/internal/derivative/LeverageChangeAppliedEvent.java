package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.event.internal.CoreEvent;

public record LeverageChangeAppliedEvent(String tradingPair, int changedTo) implements CoreEvent {
}
