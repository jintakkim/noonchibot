package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.derivative.PositionMode;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

public record PositionModeChangeIORequestedEvent(PositionMode wantTo) implements CoreEvent {
}
