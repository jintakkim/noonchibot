package com.hotak.noonchibot.core.datatype;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@RequiredArgsConstructor
public class UserStreamTracker {
    private final UserStreamTrackerDatasource datasource;

    public final BlockingQueue<Object> userStream = new LinkedBlockingQueue<>();
    private volatile Thread trackingThread;
    private volatile boolean running = false;

    /**
     * 사용자 스트림 추적기
     * 거래소의 사용자 데이터 스트림(잔고, 주문 상태 등)을 수신하고 큐에 저장
     */
    public synchronized void start() {
        if (trackingThread != null && trackingThread.isAlive()) return;
        stop();
        running = true;
        trackingThread = Thread.ofVirtual()
                .name("user-stream-tracker")
                .start(() -> {
                    try {
                        datasource.listenForUserStream(userStream);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.debug("User stream tracker interrupted");
                    } catch (Exception e) {
                        log.error("Error in user stream tracker", e);
                    }
                });
    }

    /**
     * 사용자 스트림 수신 중지
     */
    public synchronized void stop() {
        running = false;

        if (trackingThread != null && trackingThread.isAlive()) {
            trackingThread.interrupt();
            try {
                trackingThread.join(5000);  // 5초 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        datasource.stop();
        trackingThread = null;
        userStream.clear();
    }

    public boolean isRunning() {
        return running && trackingThread != null && trackingThread.isAlive();
    }

    public Instant getLastRecvTime() {
        return datasource.getLastRecvTime();
    }

}
