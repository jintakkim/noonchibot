package com.hotak.noonchibot.core.config;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Configuration
public class WebsocketConfig {
    @Bean
    public WebSocketClient webSocketClient() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        int bufferSize = 2 * 1024 * 1024;
        container.setDefaultMaxTextMessageBufferSize(bufferSize);
        return new StandardWebSocketClient(container);
    }
}
