package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.event.internal.CoreEvent;

public sealed interface LeverageChangeEvent extends CoreEvent {

    record EnsureCommand(String tradingPair, int wantTo) implements LeverageChangeEvent {}

    record IORequested(String tradingPair, int wantTo) implements LeverageChangeEvent {}

    record Applied(String tradingPair, int changedTo) implements LeverageChangeEvent {}

    record Failed(Throwable cause) implements LeverageChangeEvent {}
}