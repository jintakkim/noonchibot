package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.derivative.PositionMode;

public record PositionModeChangedEvent(PositionMode newMode) implements Event {
}
