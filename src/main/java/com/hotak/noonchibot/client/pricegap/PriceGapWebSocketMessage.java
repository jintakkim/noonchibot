package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.core.pricegap.PriceGapSnapshot;

import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.LAST_TRADE_PRICE_GAP_EVENT;

public record PriceGapWebSocketMessage(String type, PriceGapSnapshot data) {
    public PriceGapWebSocketMessage(PriceGapSnapshot data) {
        this(LAST_TRADE_PRICE_GAP_EVENT, data);
    }
}
