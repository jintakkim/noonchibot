package com.hotak.noonchibot.client.websocket;

import static com.hotak.noonchibot.client.websocket.WebSocketErrorConstants.ERROR_TYPE;

public class WebSocketRequestException extends RuntimeException {
    private final String code;

    public WebSocketRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public WebSocketErrorMessage toMessage() {
        return new WebSocketErrorMessage(
                ERROR_TYPE,
                new ErrorData(code, getMessage())
        );
    }

    public record WebSocketErrorMessage(String type, ErrorData data) {
    }

    public record ErrorData(String code, String message) {
    }
}
