package com.hotak.noonchibot.core.datatype;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

class UserStreamTrackerTest {

    private UserStreamTracker tracker;
    private MockUserStreamDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new MockUserStreamDataSource();
        tracker = new UserStreamTracker(dataSource);
    }

    @AfterEach
    void tearDown() {
        tracker.stop();
    }

    @Test
    @DisplayName("start 후 메시지를 큐에서 수신할 수 있다")
    void receivesMessagesAfterStart() throws InterruptedException {
        tracker.start();
        dataSource.emitMessage(new OrderUpdateMessage("order-1", "FILLED"));

        Object msg = tracker.userStream.poll(1, TimeUnit.SECONDS);
        assertThat(msg).isInstanceOf(OrderUpdateMessage.class);
    }

    @Test
    @DisplayName("stop 후 큐가 비워진다")
    void queueClearedAfterStop() throws InterruptedException {
        tracker.start();
        dataSource.emitMessage(new OrderUpdateMessage("order-1", "FILLED"));
        tracker.userStream.poll(1, TimeUnit.SECONDS);

        tracker.stop();

        assertThat(tracker.userStream).isEmpty();
    }

    @Test
    @DisplayName("stop 후 재시작하면 메시지를 다시 수신한다")
    void canRestartAndReceiveMessages() throws InterruptedException {
        tracker.start();
        tracker.stop();
        tracker.start();

        dataSource.emitMessage(new OrderUpdateMessage("order-2", "NEW"));
        Object msg = tracker.userStream.poll(1, TimeUnit.SECONDS);

        assertThat(msg).isInstanceOf(OrderUpdateMessage.class);
    }

    static class MockUserStreamDataSource implements UserStreamTrackerDatasource {

        private volatile Instant lastRecvTime = Instant.EPOCH;
        private volatile boolean listening = false;
        private volatile boolean stopped = false;
        private final AtomicInteger startCount = new AtomicInteger(0);
        private BlockingQueue<Object> registeredQueue;

        @Override
        public void listenForUserStream(BlockingQueue<Object> queue) {
            // non-blocking - 큐 등록만 하고 리턴
            startCount.incrementAndGet();
            listening = true;
            stopped = false;
            registeredQueue = queue;
        }

        @Override
        public Instant getLastRecvTime() {
            return lastRecvTime;
        }

        @Override
        public void stop() {
            listening = false;
            stopped = true;
            registeredQueue = null;
        }

        @Override
        public boolean isConnected() {
            return stopped;
        }

        // 테스트 헬퍼 - WS 스레드 역할
        public void emitMessage(Object message) {
            if (registeredQueue != null) {
                lastRecvTime = Instant.now();
                registeredQueue.offer(message);
            }
        }

        public boolean isListening() { return listening; }
        public boolean isStopped() { return stopped; }
        public int getStartCount() { return startCount.get(); }
    }


    record OrderUpdateMessage(String orderId, String status) {}
}