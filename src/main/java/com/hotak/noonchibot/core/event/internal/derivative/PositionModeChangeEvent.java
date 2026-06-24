package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.derivative.PositionMode;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

public sealed interface PositionModeChangeEvent extends CoreEvent {
    record EnsureCommand(PositionMode wantTo) implements PositionModeChangeEvent {}

    record IORequested(PositionMode wantTo) implements PositionModeChangeEvent {}

    record Applied(PositionMode changedTo) implements PositionModeChangeEvent {}

    record Failed(Throwable cause) implements PositionModeChangeEvent {}
}
