package com.hotak.noonchibot.connector.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class WsAssistant implements AutoCloseable {
    private final WebSocketClient webSocketClient;
    private WebSocketSession session;
    private final List<WsPreProcessor> preProcessors;
    private final List<WsPostProcessor> postProcessors;
    private final Authenticator authenticator;
    private final ObjectMapper objectMapper;
    private final BlockingQueue<WsResponse> inbound = new LinkedBlockingQueue<>();
    private volatile long lastRecvTime;

    public void connect(String wsUrl) {
        try {
            session = webSocketClient.execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession s, TextMessage msg) {
                    onMessage(msg.getPayload());
                }

                @Override
                public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    // reconnect 훅
                }
            }, wsUrl).get();

            startHeartbeat();
        } catch (Exception e) {
            throw new RuntimeException("WebSocket connection failed", e);
        }
    }

    public void send(WsRequest request) {
        WsRequest finalRequest = applyAuthentication(applyPreProcessors(request));
        try {
            String payload = objectMapper.writeValueAsString(finalRequest.payload());
            session.sendMessage(new TextMessage(payload));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void subscribe(WsRequest request) {
        send(request);
    }

    public WsResponse receive() throws InterruptedException {
        WsResponse response = inbound.take();
        return applyPostProcessors(response);
    }

    public WsResponse receive(long timeout, TimeUnit unit) throws InterruptedException {
        WsResponse response = inbound.poll(timeout, unit);
        if (response == null) return null;
        return applyPostProcessors(response);
    }

    /**
     * WebSocketHandler에서 호출 — 메시지 도착 시 내부 큐에 적재
     */
    void onMessage(String rawMessage) {
        lastRecvTime = System.currentTimeMillis();
        try {
            JsonNode data = objectMapper.readTree(rawMessage);
            inbound.add(new WsResponse(data));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse WebSocket message", e);
        }
    }

    // ── processor pipeline (RestAssistant와 동일 패턴) ──

    private WsRequest applyPreProcessors(WsRequest request) {
        WsRequest current = request;
        for (WsPreProcessor processor : preProcessors) {
            current = processor.process(current);
        }
        return current;
    }

    private WsRequest applyAuthentication(WsRequest request) {
        if (authenticator != null && request.authRequired()) {
            return authenticator.wsAuthenticate(request);
        }
        return request;
    }

    private WsResponse applyPostProcessors(WsResponse response) {
        WsResponse current = response;
        for (WsPostProcessor processor : postProcessors) {
            current = processor.process(current);
        }
        return current;
    }

    // ── lifecycle ──

    public long getLastRecvTime() {
        return lastRecvTime;
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    @Override
    public void close() {
        if (session != null && session.isOpen()) {
            try { session.close(); } catch (Exception e) { /* log */ }
        }
    }
}