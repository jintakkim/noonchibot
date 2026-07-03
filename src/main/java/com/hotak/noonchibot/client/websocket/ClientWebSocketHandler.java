package com.hotak.noonchibot.client.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.INTERNAL_ERROR;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.INVALID_REQUEST;
import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.METHOD_REQUIRED;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.METHOD_FIELD;
import static com.hotak.noonchibot.client.websocket.WebSocketProtocol.SUBSCRIPTION_FIELD;

@Slf4j
@RequiredArgsConstructor
@Component
public class ClientWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final WebSocketSessions sessions;
    private final WebSocketRequestDispatcher dispatcher;
    private final List<WebSocketSessionLifecycle> sessionLifecycles;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode request = objectMapper.readTree(message.getPayload());
            JsonNode method = request.get(METHOD_FIELD);
            if (method == null || !method.isString()) {
                throw new WebSocketRequestException(
                        INVALID_REQUEST,
                        METHOD_REQUIRED
                );
            }
            dispatcher.dispatch(session.getId(), method.asText(), request.get(SUBSCRIPTION_FIELD));
        } catch (WebSocketRequestException exception) {
            sessions.send(session.getId(), exception.toMessage());
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            sessions.send(session.getId(), new WebSocketRequestException(
                    INTERNAL_ERROR,
                    cause.getMessage()
            ).toMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("client websocket transport error. session={}", session.getId(), exception);
        removeSession(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void removeSession(String sessionId) {
        sessionLifecycles.forEach(lifecycle -> lifecycle.sessionClosed(sessionId));
        sessions.remove(sessionId);
    }
}
