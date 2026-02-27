package com.hotak.noonchibot.core.datatype;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public interface UserStreamTrackerDatasource {
    /**
     * 사용자 스트림 수신 (async)
     *
     * @param queue 수신된 메시지를 넣을 큐
     */
    void listenForUserStream(BlockingQueue<Object> queue);

    /**
     * 마지막 메시지 수신 시간
     */
    Instant getLastRecvTime();

    /**
     * 리소스 정리
     */
    void stop();

    boolean isConnected();
}
