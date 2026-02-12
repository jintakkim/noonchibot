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

    @Nested
    @DisplayName("start")
    class StartTest {

        @Test
        @DisplayName("스트림 수신 시작")
        void startsListening() throws InterruptedException {
            tracker.start();

            // 시작 대기
            Thread.sleep(100);

            assertThat(tracker.isRunning()).isTrue();
            assertThat(dataSource.isListening()).isTrue();
        }

        @Test
        @DisplayName("중복 start 호출 무시")
        void ignoresDuplicateStart() throws InterruptedException {
            tracker.start();
            tracker.start();
            tracker.start();

            Thread.sleep(100);

            assertThat(dataSource.getStartCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("stop 후 재시작 가능")
        void canRestartAfterStop() throws InterruptedException {
            tracker.start();
            Thread.sleep(100);

            tracker.stop();
            assertThat(tracker.isRunning()).isFalse();

            tracker.start();
            Thread.sleep(100);

            assertThat(tracker.isRunning()).isTrue();
            assertThat(dataSource.getStartCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("stop")
    class StopTest {

        @Test
        @DisplayName("스트림 수신 중지")
        void stopsListening() throws InterruptedException {
            tracker.start();
            Thread.sleep(100);

            tracker.stop();

            assertThat(tracker.isRunning()).isFalse();
            assertThat(dataSource.isStopped()).isTrue();
        }

        @Test
        @DisplayName("시작 전 stop 호출해도 안전")
        void safeToStopBeforeStart() {
            tracker.stop();  // 예외 없이 통과

            assertThat(tracker.isRunning()).isFalse();
        }

        @Test
        @DisplayName("중복 stop 호출 안전")
        void safeToStopMultipleTimes() throws InterruptedException {
            tracker.start();
            Thread.sleep(100);

            tracker.stop();
            tracker.stop();
            tracker.stop();

            assertThat(tracker.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("userStream 큐")
    class UserStreamQueueTest {

        @Test
        @DisplayName("메시지가 큐에 추가됨")
        void messagesAddedToQueue() throws InterruptedException {
            tracker.start();

            // 메시지 발행
            dataSource.emitMessage(new OrderUpdateMessage("order-1", "FILLED"));
            dataSource.emitMessage(new BalanceUpdateMessage("USDT", "1000"));

            // 큐에서 수신
            BlockingQueue<Object> queue = tracker.userStream;

            Object msg1 = queue.poll(1, TimeUnit.SECONDS);
            Object msg2 = queue.poll(1, TimeUnit.SECONDS);

            assertThat(msg1).isInstanceOf(OrderUpdateMessage.class);
            assertThat(msg2).isInstanceOf(BalanceUpdateMessage.class);
        }

        @Test
        @DisplayName("stop 시 큐 비워짐")
        void queueClearedOnStop() throws InterruptedException {
            tracker.start();

            dataSource.emitMessage(new OrderUpdateMessage("order-1", "FILLED"));
            Thread.sleep(100);

            tracker.stop();

            assertThat(tracker.userStream).isEmpty();
        }
    }

    @Nested
    @DisplayName("lastRecvTime")
    class LastRecvTimeTest {

        @Test
        @DisplayName("초기값은 EPOCH")
        void initialValueIsEpoch() {
            assertThat(tracker.getLastRecvTime()).isEqualTo(Instant.EPOCH);
        }

        @Test
        @DisplayName("메시지 수신 시 갱신됨")
        void updatedOnMessageReceived() throws InterruptedException {
            tracker.start();

            Instant before = Instant.now();
            dataSource.emitMessage(new OrderUpdateMessage("order-1", "FILLED"));
            Thread.sleep(100);
            Instant after = Instant.now();

            assertThat(tracker.getLastRecvTime())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }
    }

    static class MockUserStreamDataSource implements UserStreamTrackerDatasource {

        private volatile Instant lastRecvTime = Instant.EPOCH;
        private volatile boolean listening = false;
        private volatile boolean stopped = false;
        private final AtomicInteger startCount = new AtomicInteger(0);
        private final BlockingQueue<Object> internalQueue = new java.util.concurrent.LinkedBlockingQueue<>();

        @Override
        public void listenForUserStream(BlockingQueue<Object> queue) throws InterruptedException {
            startCount.incrementAndGet();
            listening = true;
            stopped = false;

            while (!Thread.currentThread().isInterrupted()) {
                Object message = internalQueue.poll(100, TimeUnit.MILLISECONDS);
                if (message != null) {
                    lastRecvTime = Instant.now();
                    queue.put(message);
                }
            }
        }

        @Override
        public Instant getLastRecvTime() {
            return lastRecvTime;
        }

        @Override
        public void stop() {
            listening = false;
            stopped = true;
        }

        // 테스트 헬퍼
        public void emitMessage(Object message) {
            internalQueue.offer(message);
        }

        public boolean isListening() {
            return listening;
        }

        public boolean isStopped() {
            return stopped;
        }

        public int getStartCount() {
            return startCount.get();
        }
    }

    // 테스트용 메시지 타입
    record OrderUpdateMessage(String orderId, String status) {}
    record BalanceUpdateMessage(String currency, String amount) {}
}