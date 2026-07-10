package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.client.websocket.WebSocketSessionLifecycle;
import com.hotak.noonchibot.core.pricegap.PriceGapSubscriptionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PriceGapSessionLifecycle implements WebSocketSessionLifecycle {
    private final PriceGapSubscriptionRegistry subscriptionRegistry;

    @Override
    public void sessionClosed(String sessionId) {
        subscriptionRegistry.removeSubscriber(sessionId);
    }
}
