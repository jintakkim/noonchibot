package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class WsConnectionImpl extends TextWebSocketHandler implements WsConnection {
    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final Authenticator authenticator;
    private final List<WsPreProcessor> preProcessors;
    private final List<WsPostProcessor> postProcessors;
    private final WsConnectionListener listener;
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
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException closeError) {
                log.error("Failed to close WebSocket after send error", closeError);
            }
        }
    }

    @Override
    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    @Override
    public void disconnect(CloseStatus status) {
        if (isConnected()) {
            try {
                session.close(status);
            } catch (IOException e) {
                log.error("Failed to close WebSocket session", e);
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 기본 session인 스텐다드 세션은 병렬 전송 요청 처리가 안된다. 병렬 -> 순차로 바꿔주는 데코레이터 필수
        this.session = new ConcurrentWebSocketSessionDecorator(
                session,
                SEND_TIME_LIMIT_MILLIS,
                SEND_BUFFER_SIZE_LIMIT_BYTES
        );
        listener.onConnected(this);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: endpoint={}, status={}", endpoint(session), status);
        this.session = null;
        listener.onClosed(status);
    }

    private String endpoint(WebSocketSession session) {
        if (session.getUri() == null) return "unknown";
        return session.getUri().getScheme() + "://" + session.getUri().getAuthority();
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        listener.onError(exception);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WsResponse response = new WsResponse(message.getPayload(), WsResponse.MessageType.TEXT);
        for (WsPostProcessor processor : postProcessors) {
            response = processor.process(response);
        }
        listener.onMessage(response);
    }
}
