package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.core.event.internal.WebsocketUnavailableEvent;
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
}
