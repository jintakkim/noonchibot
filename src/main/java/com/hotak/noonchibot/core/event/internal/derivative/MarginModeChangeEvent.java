package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.derivative.MarginMode;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

public sealed interface MarginModeChangeEvent extends CoreEvent {
    record EnsureCommand(String tradingPair, MarginMode wantTo) implements MarginModeChangeEvent {}

    record IORequested(String tradingPair, MarginMode wantTo) implements MarginModeChangeEvent {}

    record Applied(String tradingPair, MarginMode changedTo) implements MarginModeChangeEvent {}

    record Failed(Throwable cause) implements MarginModeChangeEvent {}
}
