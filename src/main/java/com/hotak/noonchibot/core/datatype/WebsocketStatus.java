package com.hotak.noonchibot.core.datatype;

import java.time.Instant;

public interface WebsocketStatus {
    boolean isConnected();
    /**
     * @return 아무 응답도 받지 못한 초기에는 null이다.
     */
    Instant getLastRecvTime();
}
