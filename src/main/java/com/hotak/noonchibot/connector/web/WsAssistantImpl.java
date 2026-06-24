package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class WsAssistantImpl implements WsAssistant {
    private final WebSocketClient webSocketClient;
    private final WebSocketHttpHeaders headers;
    private final List<WsPreProcessor> preProcessors;
    private final List<WsPostProcessor> postProcessors;
    private final ObjectMapper objectMapper;
    private final Authenticator authenticator;

    @Override
    public WsConnection connect(URI wsUrl) {
        WsConnectionImpl wsConnection = new WsConnectionImpl(objectMapper, authenticator, preProcessors, postProcessors);
        webSocketClient.execute(wsConnection, headers, wsUrl).join();
        return wsConnection;
    }
}