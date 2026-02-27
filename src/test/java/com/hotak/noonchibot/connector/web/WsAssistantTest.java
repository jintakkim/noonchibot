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

    private WsAssistant wsAssistant;
    private WebSocketClient webSocketClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        webSocketClient = mock(WebSocketClient.class);
        objectMapper = new ObjectMapper();
        wsAssistant = new WsAssistant(
                webSocketClient,
                new WebSocketHttpHeaders(),
                List.of(),
                List.of(),
                objectMapper,
                null
        );
    }

    @Test
    @DisplayName("연결 후 메시지를 수신할 수 있다")
    void receivesMessageAfterConnect() throws InterruptedException {
        WsConnection conn = connectWithMockSession();
        // 서버에서 메시지 수신 시뮬레이션
        conn.handleTextMessage(null, new TextMessage("message"));
        WsResponse response = conn.take();
        assertThat(response.data()).contains("message");
    }

    @Test
    @DisplayName("의도적인 연결 해제 시 take에서 null이 반환된다")
    void returnsNullOnIntentionalDisconnect() throws InterruptedException {
        WsConnection conn = connectWithMockSession();
        conn.disconnect();
        conn.afterConnectionClosed(null, CloseStatus.NORMAL);
        assertThat(conn.take()).isNull();
    }

    @Test
    @DisplayName("비의도적인 연결 해제 시 take에서 예외가 발생한다")
    void throwsOnUnintentionalDisconnect() {
        WsConnection conn = connectWithMockSession();
        conn.afterConnectionClosed(null, CloseStatus.GOING_AWAY);
        assertThatThrownBy(conn::take).isInstanceOf(WebsocketDisconnectedException.class);
    }

    @Test
    @DisplayName("연결되지 않은 상태에서 send 시 예외가 발생한다")
    void throwsWhenSendingWithoutConnection() {
        WsRequest request = new WsRequest(Map.of("method", "SUBSCRIBE"), false);
        assertThatThrownBy(() -> wsAssistant.send(request)).isInstanceOf(WebSocketNotConnectedException.class);
    }

    @Test
    @DisplayName("preProcessor가 등록되었다면 send 시 preProcessor가 적용된다")
    void appliesPreProcessorsOnSend() {
        WsPreProcessor wsPreProcessor = mock(WsPreProcessor.class);
        wsAssistant = new WsAssistant(
                webSocketClient, new WebSocketHttpHeaders(),
                List.of(wsPreProcessor), List.of(),
                objectMapper, null
        );
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        WsConnection conn = connectWithMockSession(session);
        WsRequest request = new WsRequest(Map.of("method", "SUBSCRIBE"), false);
        conn.send(request);
        verify(wsPreProcessor, times(1)).process(request);
    }

    @Test
    @DisplayName("authRequired일 때 authenticator가 적용된다")
    void appliesAuthenticatorWhenRequired() {
        Authenticator authenticator = mock(Authenticator.class);
        WsRequest authRequest = new WsRequest(Map.of("method", "SUBSCRIBE", "signed", true), true);
        when(authenticator.wsAuthenticate(any())).thenReturn(authRequest);

        wsAssistant = new WsAssistant(
                webSocketClient, new WebSocketHttpHeaders(),
                List.of(), List.of(),
                objectMapper, authenticator
        );

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        connectWithMockSession(session);
        wsAssistant.send(new WsRequest(Map.of("method", "SUBSCRIBE"), true));
        verify(authenticator).wsAuthenticate(any());
    }

    @Test
    @DisplayName("postProcessor가 수신 메시지에 적용된다")
    void appliesPostProcessorsOnReceive() throws InterruptedException {
        WsPostProcessor uppercase = resp -> new WsResponse(
                resp.data().toUpperCase(), resp.messageType()
        );
        wsAssistant = new WsAssistant(
                webSocketClient, new WebSocketHttpHeaders(),
                List.of(), List.of(uppercase),
                objectMapper, null
        );

        WsConnection conn = connectWithMockSession();
        conn.handleTextMessage(null, new TextMessage("hello"));

        WsResponse response = conn.take();
        assertThat(response.data()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("이미 연결된 상태에서 connect 호출 시 기존 연결을 반환한다")
    void returnsExistingConnectionWhenAlreadyConnected() {
        WsConnection first = connectWithMockSession();
        WsConnection second = wsAssistant.connect(URI.create("wss://test.com"));
        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("여러 메시지를 순서대로 수신한다")
    void receivesMessagesInOrder() throws InterruptedException {
        WsConnection conn = connectWithMockSession();

        conn.handleTextMessage(null, new TextMessage("first"));
        conn.handleTextMessage(null, new TextMessage("second"));
        conn.handleTextMessage(null, new TextMessage("third"));

        assertThat(conn.take().data()).isEqualTo("first");
        assertThat(conn.take().data()).isEqualTo("second");
        assertThat(conn.take().data()).isEqualTo("third");
    }

    private WsConnection connectWithMockSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return connectWithMockSession(session);
    }

    private WsConnection connectWithMockSession(WebSocketSession session) {
        when(webSocketClient.execute(any(WsConnection.class), any(WebSocketHttpHeaders.class), any(URI.class))).thenAnswer(invocation -> {
            WsConnection conn = invocation.getArgument(0);
            conn.afterConnectionEstablished(session);
            return CompletableFuture.completedFuture(null);
        });
        return wsAssistant.connect(URI.create("wss://test.com"));
    }
}