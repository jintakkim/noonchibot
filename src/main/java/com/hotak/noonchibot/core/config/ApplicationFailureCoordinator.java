package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.core.event.internal.WebsocketUnavailableEvent;
import com.hotak.noonchibot.core.event.internal.ExchangeUnavailableEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationFailureCoordinator {
    private final ConfigurableApplicationContext applicationContext;

    @EventListener
    public void onWebsocketUnavailable(WebsocketUnavailableEvent event) {
        log.error("Critical WebSocket unavailable; shutting down application");
        applicationContext.close();
    }

    @EventListener
    public void onExchangeUnavailable(ExchangeUnavailableEvent event) {
        log.error("Critical exchange operation unavailable; exchange={}, operation={}, pair={}; shutting down application",
                event.exchange(), event.operation(), event.tradingPair(), event.cause());
        applicationContext.close();
    }
}
