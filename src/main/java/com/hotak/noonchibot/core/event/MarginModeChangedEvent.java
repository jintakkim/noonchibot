package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.derivative.MarginMode;

public record MarginModeChangedEvent(String tradingPair, MarginMode newMode) implements ExchangeEvent {}
