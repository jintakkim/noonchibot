package com.hotak.noonchibot.connector.web.testutils;

import com.hotak.noonchibot.connector.web.WsConnection;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MockWsConnection implements WsConnection {
    private final URI wsUri;
    private WsResponse ackResponse;
    public final List<WsRequest> sentRequests = new ArrayList<>();
    private final BlockingQueue<WsResponse> incoming = new LinkedBlockingQueue<>();
    private volatile boolean connected = true;

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
        if(ackResponse != null) {
            incoming.add(ackResponse);
        }
    }

    public void setAckResponse(WsResponse ackResponse) {
        this.ackResponse = ackResponse;
    }

    public void push(WsResponse response) {
        incoming.add(response);
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public WsResponse take() throws InterruptedException {
        return incoming.take();
    }

    public void clearRequests() {
        sentRequests.clear();
    }
}
