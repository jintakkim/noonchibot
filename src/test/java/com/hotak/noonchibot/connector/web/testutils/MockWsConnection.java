package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.web.WsConnection;
import com.hotak.noonchibot.connector.web.WsConnectionListener;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import org.springframework.web.socket.CloseStatus;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class MockWsConnection implements WsConnection {
    private final URI wsUri;
    private WsResponse ackResponse;
    private Function<WsRequest, WsResponse> responseFactory;
    public final List<WsRequest> sentRequests = new ArrayList<>();
    private volatile boolean connected = true;
    private volatile WsConnectionListener listener;

    public MockWsConnection(URI wsUri, WsResponse ackResponse) {
        this.wsUri = wsUri;
        this.ackResponse = ackResponse;
    }

    public MockWsConnection(URI wsUri) {
        this(wsUri, null);
    }

    public URI getUri() {
        return wsUri;
    }

    public WsServer getScenario() {
        return new WsServer(this);
    }

    @Override
    public void send(WsRequest request) {
        sentRequests.add(request);
        WsResponse response = responseFactory == null ? ackResponse : responseFactory.apply(request);
        if (response != null && listener != null) listener.onMessage(response);
    }

    public void setAckResponse(WsResponse ackResponse) {
        this.ackResponse = ackResponse;
    }

    public void setResponseFactory(Function<WsRequest, WsResponse> responseFactory) {
        this.responseFactory = responseFactory;
    }

    public void push(WsResponse response) {
        if (listener == null) throw new IllegalStateException("WebSocket is not connected");
        listener.onMessage(response);
    }

    @Override
    public void disconnect(CloseStatus status) {
        connected = false;
        if (listener != null) listener.onClosed(status);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public void connect(WsConnectionListener listener) {
        this.listener = listener;
        this.connected = true;
        listener.onConnected(this);
    }

    public void clearRequests() {
        sentRequests.clear();
    }
}
