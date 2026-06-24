package com.hotak.noonchibot.connector.hyperliquid;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.LifecycleComponent;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.event.EventListener;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.EventSubscriber;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class LiquidationEventPublisher implements LifecycleComponent {
    private final ExchangeEventPublisher eventPublisher;
    private final EventSubscriber eventSubscriber;
    private final RestAssistantImpl restAssistant;
    private final IoExecutor ioExecutor;
    private final ObjectMapper objectMapper;

    private final EventListener<WSLiquidationEvent> wsLiquidationEventListener = this::onLiquidationEventOccurred;

    public LiquidationEventPublisher(
            ExchangeEventPublisher eventPublisher,
            EventSubscriber eventSubscriber,
            IoExecutor ioExecutor,
            RestAssistantImpl restAssistant,
            ObjectMapper objectMapper
            ) {
        this.eventPublisher = eventPublisher;
        this.eventSubscriber = eventSubscriber;
        this.restAssistant = restAssistant;
        this.ioExecutor = ioExecutor;
        this.objectMapper = objectMapper;
    }

    @VisibleForTesting
    void onLiquidationEventOccurred(WSLiquidationEvent event) {
        ioExecutor.submit(() -> {
            try {


            } catch (Exception e) {
                log.error("error occurred while processing liquidationEvent", e);
            }
        });

    }


    @Override
    public void start() {
        eventSubscriber.subscribe(WSLiquidationEvent.class, wsLiquidationEventListener);
    }

    @Override
    public void shutdown() {
        eventSubscriber.unsubscribe(WSLiquidationEvent.class, wsLiquidationEventListener);
    }
}
