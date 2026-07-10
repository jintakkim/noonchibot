package com.hotak.noonchibot.client.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketRequestExceptionTest {
    @Test
    void toMessage_returnsErrorEnvelope() {
        WebSocketRequestException.WebSocketErrorMessage message = new WebSocketRequestException(
                "INVALID_REQUEST",
                "method is required"
        ).toMessage();

        assertThat(message.type()).isEqualTo("error");
        assertThat(message.data().code()).isEqualTo("INVALID_REQUEST");
        assertThat(message.data().message()).isEqualTo("method is required");
    }
}
