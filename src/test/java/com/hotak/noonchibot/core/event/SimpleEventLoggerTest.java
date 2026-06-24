package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SimpleEventLoggerTest {
    private SimpleEventLogger logger;

    @BeforeEach
    void setUp() {
        logger = new SimpleEventLogger("test-source");
    }

    @Nested
    @DisplayName("기본 속성")
    class BasicPropertiesTest {

        @Test
        @DisplayName("eventSource 반환")
        void returnsEventSource() {
            assertThat(logger.getEventSource()).isEqualTo("test-source");
        }

        @Test
        @DisplayName("초기 상태에서 빈 이벤트 로그")
        void emptyEventLogInitially() {
            assertThat(logger.getEventLog()).isEmpty();
        }
    }

    @Nested
    @DisplayName("onEvent")
    class OnEventTest {

        @Test
        @DisplayName("일반 이벤트 로깅")
        void logsGenericEvent() {
            TestEvent event = new TestEvent("test");
            logger.onEvent(event);
            assertThat(logger.getEventLog()).containsExactly(event);
        }

        @Test
        @DisplayName("OrderFilledEvent 별도 보관")
        void logsOrderFilledEventSeparately() {
            OrderFilledEvent event = createOrderFilledEvent();
            logger.onEvent(event);
            assertThat(logger.getEventLog()).containsExactly(event);
        }

        @Test
        @DisplayName("여러 타입 이벤트 혼합 로깅")
        void logsMixedEvents() {
            TestEvent generic = new TestEvent("generic");
            OrderFilledEvent filled = createOrderFilledEvent();

            logger.onEvent(generic);
            logger.onEvent(filled);

            assertThat(logger.getEventLog()).containsExactly(generic, filled);
        }

        @Test
        @DisplayName("일반 이벤트 최대 50개 유지")
        void limitsGenericEventsTo50() {
            for (int i = 0; i < 60; i++) {
                logger.onEvent(new TestEvent("event-" + i));
            }
            List<Object> log = logger.getEventLog();
            assertThat(log).hasSize(50);
            // 처음 10개는 삭제됨
            assertThat(((TestEvent) log.getFirst()).name()).isEqualTo("event-10");
            assertThat(((TestEvent) log.getLast()).name()).isEqualTo("event-59");
        }

        @Test
        @DisplayName("OrderFilledEvent는 제한 없이 전부 보관")
        void keepsAllOrderFilledEvents() {
            for (int i = 0; i < 100; i++) {
                logger.onEvent(createOrderFilledEvent());
            }
            assertThat(logger.getEventLog()).hasSize(100);
        }
    }

    @Test
    @DisplayName("모든 이벤트 삭제")
    void clearsAllEvents() {
        logger.onEvent(new TestEvent("test"));
        logger.onEvent(createOrderFilledEvent());

        logger.clear();

        assertThat(logger.getEventLog()).isEmpty();
    }

    @Nested
    @DisplayName("waitFor")
    class WaitForTest {

        @Test
        @DisplayName("이벤트 발생 시 Future 완료")
        void completesWhenEventOccurs() throws ExecutionException, InterruptedException {
            CompletableFuture<TestEvent> future = logger.waitFor(TestEvent.class, Duration.ofSeconds(5));

            TestEvent event = new TestEvent("awaited");
            logger.onEvent(event);

            assertThat(future.get()).isEqualTo(event);
        }

        @Test
        @DisplayName("타임아웃 시 예외 발생")
        void throwsOnTimeout() {
            CompletableFuture<TestEvent> future = logger.waitFor(TestEvent.class, Duration.ofMillis(100));

            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(TimeoutException.class);
        }

        @Test
        @DisplayName("다른 타입 이벤트는 무시")
        void ignoresDifferentEventType() {
            CompletableFuture<TestEvent> future = logger.waitFor(TestEvent.class, Duration.ofMillis(100));
            logger.onEvent(createOrderFilledEvent());  // 다른 타입
            assertThat(future.isDone()).isFalse();
        }

        @Test
        @DisplayName("여러 waiter 동시 대기")
        void multipleWaiters() throws ExecutionException, InterruptedException {
            CompletableFuture<TestEvent> future1 = logger.waitFor(TestEvent.class, Duration.ofSeconds(5));
            CompletableFuture<TestEvent> future2 = logger.waitFor(TestEvent.class, Duration.ofSeconds(5));
            TestEvent event = new TestEvent("shared");
            logger.onEvent(event);
            assertThat(future1.get()).isEqualTo(event);
            assertThat(future2.get()).isEqualTo(event);
        }

        @Test
        @DisplayName("서로 다른 타입 동시 대기")
        void differentTypeWaiters() throws ExecutionException, InterruptedException {
            CompletableFuture<TestEvent> testFuture = logger.waitFor(TestEvent.class, Duration.ofSeconds(5));
            CompletableFuture<OrderFilledEvent> filledFuture = logger.waitFor(OrderFilledEvent.class, Duration.ofSeconds(5));

            TestEvent testEvent = new TestEvent("test");
            OrderFilledEvent filledEvent = createOrderFilledEvent();

            logger.onEvent(testEvent);
            logger.onEvent(filledEvent);

            assertThat(testFuture.get()).isEqualTo(testEvent);
            assertThat(filledFuture.get()).isEqualTo(filledEvent);
        }

        @Test
        @DisplayName("완료 후 waiting에서 제거")
        void removesFromWaitingAfterCompletion() throws ExecutionException, InterruptedException {
            CompletableFuture<TestEvent> future = logger.waitFor(TestEvent.class, Duration.ofSeconds(5));

            logger.onEvent(new TestEvent("done"));
            future.get();

            // 두 번째 이벤트는 이미 완료된 future에 영향 없음
            logger.onEvent(new TestEvent("second"));
            assertThat(future.get().name()).isEqualTo("done");
        }
    }




    record TestEvent(String name) {}

    private OrderFilledEvent createOrderFilledEvent() {
        return new OrderFilledEvent(
                Instant.EPOCH,
                "1",
                "BTC-USDT",
                TradeType.BUY,
                OrderType.MARKET,
                null,
                BigDecimal.ONE,
                null,
                "1",
                "1"
        );
    }
}
