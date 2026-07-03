package com.hotak.noonchibot.client.websocket;

public final class WebSocketProtocol {
    public static final String METHOD_FIELD = "method";
    public static final String SUBSCRIPTION_FIELD = "subscription";
    public static final String TYPE_FIELD = "type";
    public static final String PAIR_FIELD = "pair";
    public static final String PAIRS_FIELD = "pairs";

    public static final String SUBSCRIBE_METHOD = "subscribe";
    public static final String UNSUBSCRIBE_METHOD = "unsubscribe";

    public static final String PRICE_GAP_SUBSCRIPTION = "priceGap";
    public static final String LAST_TRADE_PRICE_GAP_EVENT = "LAST_TRADE_PRICE_GAP";

    private WebSocketProtocol() {
    }
}
