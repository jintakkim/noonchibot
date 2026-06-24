package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@RequiredArgsConstructor
public class WsConnectionImpl extends TextWebSocketHandler implements WsConnection {
    private static final WsResponse CLOSED = new WsResponse(null, null);

    private final ObjectMapper objectMapper;
    private final Authenticator authenticator;
    private final List<WsPreProcessor> preProcessors;
    private final List<WsPostProcessor> postProcessors;
    private final BlockingQueue<WsResponse> messageQueue = new LinkedBlockingQueue<>();
    private volatile WebSocketSession session;

    @Override
    public void send(WsRequest request) {
        if (!isConnected()) {
            throw new WebSocketNotConnectedException("WebSocket session is not connected");
        }
        for (WsPreProcessor processor : preProcessors) {
            request = processor.process(request);
        }
        if (authenticator != null && request.authRequired()) {
            request = authenticator.wsAuthenticate(request);
        }
        try {
            String payload = objectMapper.writeValueAsString(request.payload());
            session.sendMessage(new TextMessage(payload));
        } catch (IOException e) {
            log.error("Failed to send message", e);
        }
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    @Override
    public void disconnect() {
        if (isConnected()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.error("Failed to close WebSocket session", e);
            }
        }
    }

    @Override
    public WsResponse take() throws InterruptedException {
        WsResponse wsResponse = messageQueue.take();
        if(wsResponse == CLOSED) {
            throw new WebsocketDisconnectedException("websocket disconnect");
        }
        return wsResponse;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("websocket connection closed {}", status.toString());
        messageQueue.add(CLOSED);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WsResponse response = new WsResponse(message.getPayload(), WsResponse.MessageType.TEXT);
        for (WsPostProcessor processor : postProcessors) {
            response = processor.process(response);
        }
        if(!messageQueue.offer(response)) {
            log.error("max size 도달");
        }
    }
}
