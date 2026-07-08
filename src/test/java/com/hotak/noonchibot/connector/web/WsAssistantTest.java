package com.hotak.noonchibot.connector.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WsAssistantTest {

    private WsAssistantImpl wsAssistant;
    private WebSocketClient webSocketClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        webSocketClient = mock(WebSocketClient.class);
        objectMapper = new ObjectMapper();
        wsAssistant = new WsAssistantImpl(
                webSocketClient,
                new WebSocketHttpHeaders(),
                List.of(),
                List.of(),
                objectMapper,
                null
        );
    }

    @Test
    @DisplayName("connect 호출 시 새로운 WsConnection을 반환한다")
    void connectReturnsNewConnection() {
        WsConnectionImpl conn = connectWithMockSession();
        assertThat(conn).isNotNull();
        assertThat(conn.isConnected()).isTrue();
    }

    @Test
    @DisplayName("connect를 두 번 호출하면 서로 다른 WsConnection을 반환한다")
    void connectReturnsDifferentConnections() {
        WsConnectionImpl first = connectWithMockSession();
        WsConnectionImpl second = connectWithMockSession();
        assertThat(second).isNotSameAs(first);
    }

    @Test
    @DisplayName("연결 후 메시지를 수신할 수 있다")
    void receivesMessageAfterConnect() throws InterruptedException {
        WsConnectionImpl conn = connectWithMockSession();
        conn.handleTextMessage(null, new TextMessage("message"));
        WsResponse response = conn.take();
        assertThat(response.data()).contains("message");
    }

    @Test
    @DisplayName("의도적인 연결 해제 시 take에서 예외가 발생한다")
    void throwsOnIntentionalDisconnect() {
        WsConnectionImpl conn = connectWithMockSession();
        conn.disconnect();
        conn.afterConnectionClosed(null, CloseStatus.NORMAL);
        assertThatThrownBy(conn::take).isInstanceOf(WebSocketDisconnectedException.class);
    }

    @Test
    @DisplayName("비의도적인 연결 해제 시 take에서 예외가 발생한다")
    void throwsOnUnintentionalDisconnect() {
        WsConnectionImpl conn = connectWithMockSession();
        conn.afterConnectionClosed(null, CloseStatus.GOING_AWAY);
        assertThatThrownBy(conn::take).isInstanceOf(WebSocketDisconnectedException.class);
    }

    @Test
    @DisplayName("preProcessor가 등록되었다면 send 시 preProcessor가 적용된다")
    void appliesPreProcessorsOnSend() {
        WsPreProcessor wsPreProcessor = mock(WsPreProcessor.class);
        wsAssistant = new WsAssistantImpl(
                webSocketClient, new WebSocketHttpHeaders(),
                List.of(wsPreProcessor), List.of(),
                objectMapper, null
        );
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        WsConnectionImpl conn = connectWithMockSession(session);
        WsRequest request = new WsRequest(Map.of("method", "SUBSCRIBE"), false);
        when(wsPreProcessor.process(any(WsRequest.class))).thenReturn(request);
        conn.send(request);
        verify(wsPreProcessor, times(1)).process(request);
    }

    @Test
    @DisplayName("authRequired일 때 authenticator가 적용된다")
    void appliesAuthenticatorWhenRequired() {
        Authenticator authenticator = mock(Authenticator.class);
        WsRequest authRequest = new WsRequest(Map.of("method", "SUBSCRIBE", "signed", true), true);
        when(authenticator.wsAuthenticate(any())).thenReturn(authRequest);

        wsAssistant = new WsAssistantImpl(
                webSocketClient, new WebSocketHttpHeaders(),
                List.of(), List.of(),
                objectMapper, authenticator
        );

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        WsConnectionImpl conn = connectWithMockSession(session);
        conn.send(new WsRequest(Map.of("method", "SUBSCRIBE"), true));
        verify(authenticator).wsAuthenticate(any());
    }

    @Test
    @DisplayName("postProcessor가 수신 메시지에 적용된다")
    void appliesPostProcessorsOnReceive() throws InterruptedException {
        WsPostProcessor uppercase = resp -> new WsResponse(
                resp.data().toUpperCase(), resp.messageType()
        );
        wsAssistant = new WsAssistantImpl(
                webSocketClient, new WebSocketHttpHeaders(),
                List.of(), List.of(uppercase),
                objectMapper, null
        );

        WsConnectionImpl conn = connectWithMockSession();
        conn.handleTextMessage(null, new TextMessage("hello"));

        WsResponse response = conn.take();
        assertThat(response.data()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("여러 메시지를 순서대로 수신한다")
    void receivesMessagesInOrder() throws InterruptedException {
        WsConnectionImpl conn = connectWithMockSession();

        conn.handleTextMessage(null, new TextMessage("first"));
        conn.handleTextMessage(null, new TextMessage("second"));
        conn.handleTextMessage(null, new TextMessage("third"));

        assertThat(conn.take().data()).isEqualTo("first");
        assertThat(conn.take().data()).isEqualTo("second");
        assertThat(conn.take().data()).isEqualTo("third");
    }

    private WsConnectionImpl connectWithMockSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return connectWithMockSession(session);
    }

    private WsConnectionImpl connectWithMockSession(WebSocketSession session) {
        when(webSocketClient.execute(any(WsConnectionImpl.class), any(WebSocketHttpHeaders.class), any(URI.class)))
                .thenAnswer(invocation -> {
                    WsConnectionImpl conn = invocation.getArgument(0);
                    conn.afterConnectionEstablished(session);
                    return CompletableFuture.completedFuture(null);
                });
        return (WsConnectionImpl) wsAssistant.connect(URI.create("wss://test.com"));
    }
}
