package com.hotak.noonchibot.connector.web;

import java.net.URI;

public interface WsAssistant {
    WsConnection connect(URI wsUrl, WsConnectionListener listener);
}
