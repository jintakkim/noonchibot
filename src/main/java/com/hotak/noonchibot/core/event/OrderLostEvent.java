package com.hotak.noonchibot.core.event;

public record OrderLostEvent(String clientOrderId) implements ExchangeEvent {}
