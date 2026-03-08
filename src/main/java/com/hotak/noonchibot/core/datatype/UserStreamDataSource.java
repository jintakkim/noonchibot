package com.hotak.noonchibot.core.datatype;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public interface UserStreamDataSource {
    /**
     * 사용자 스트림 수신
     */
    BlockingQueue<JsonNode> getUserStream();
    boolean isConnected();

    /**
     * @return 아무 응답도 받지 못한 초기에는 null이다.
     */
    Instant getLastRecvTime();
}
