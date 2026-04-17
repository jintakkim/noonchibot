package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.orderbook.FundingInfoDataSource;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessageStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

public class FundingInfoTrackerTest {
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(8);
    private static final String DEFAULT_FUNDING_COIN = "USDT";
    private static final Map<String, String> DEFAULT_SUBLIST = Map.of("BTCUSDT", "BTC-USDT", "SOLUSDT", "SOL-USDT");
    private FundingInfoTracker tracker;
    private FundingInfoDataSource dataSource;
    private IoExecutor ioExecutor;
    private MainExecutor mainExecutor;

    @BeforeEach
    void setUp() {
        dataSource = mock(FundingInfoDataSource.class);
        ioExecutor = mock(IoExecutor.class);
        mainExecutor = mock(MainExecutor.class);
        tracker = new FundingInfoTracker(
                DEFAULT_INTERVAL,
                DEFAULT_FUNDING_COIN,
                new SimpleTradingPairSymbolRegistry(DEFAULT_SUBLIST),
                dataSource,
                ioExecutor,
                mainExecutor
        );
    }

    @Nested
    @DisplayName("메시지 처리 테스트")
    class ProcessMessageTest {
        @Test
        @DisplayName("등록된 tradingPair의 메시지를 받으면 FundingInfo를 업데이트한다")
        void updateFundingInfoForRegisteredPair() {
            String tradingPair = "BTCUSDT";
            registerTradingPair(tradingPair);

            Instant nextFundingTime = Instant.now().plus(Duration.ofHours(4));
            FundingInfoMessage message = new FundingInfoMessage(
                    tradingPair,
                    Instant.now(),
                    new BigDecimal("50000"),
                    new BigDecimal("0.0001"),
                    nextFundingTime,
                    Duration.ofHours(8)
            );
            // when
            tracker.processMessage(message);

            // then
            FundingInfo info = tracker.getFundingInfo(tradingPair);
            assertThat(info).isNotNull();
            assertThat(info.getMarkPrice()).isEqualByComparingTo("50000");
            assertThat(info.getFundingRate()).isEqualByComparingTo("0.0001");
            assertThat(info.getNextFundingTime()).isEqualTo(nextFundingTime);
            assertThat(info.getFundingInterval()).isEqualTo(Duration.ofHours(8));
        }

        @Test
        @DisplayName("등록되지 않은 tradingPair의 메시지는 무시한다")
        void ignoreMessageForUnregisteredPair() {
            // given
            tracker.registerTradingPairs(Set.of("BTCUSDT"));

            FundingInfoMessage message = new FundingInfoMessage(
                    "ETHUSDT",
                    Instant.now(),
                    new BigDecimal("3000"),
                    new BigDecimal("0.0002"),
                    Instant.now(),
                    Duration.ofHours(8)
            );

            // when
            tracker.processMessage(message);

            // then
            assertThat(tracker.getFundingInfo("ETHUSDT")).isNull();
            assertThat(tracker.getFundingInfo("BTCUSDT")).isNull(); // 여전히 uninitialized
        }

        @Test
        @DisplayName("동일 tradingPair에 여러 메시지를 받으면 최신 값으로 업데이트된다")
        void updateToLatestValue() {
            // given
            String pair = "BTCUSDT";
            registerTradingPair(pair);

            FundingInfoMessage first = new FundingInfoMessage(
                    pair, Instant.now(),
                    new BigDecimal("50000"),
                    new BigDecimal("0.0001"),
                    Instant.now().plus(Duration.ofHours(4)),
                    Duration.ofHours(8)
            );
            FundingInfoMessage second = new FundingInfoMessage(
                    pair, Instant.now(),
                    new BigDecimal("51000"),
                    new BigDecimal("0.0002"),
                    Instant.now().plus(Duration.ofHours(3)),
                    Duration.ofHours(8)
            );

            // when
            tracker.processMessage(first);
            tracker.processMessage(second);

            // then
            FundingInfo info = tracker.getFundingInfo(pair);
            assertThat(info.getMarkPrice()).isEqualByComparingTo("51000");
            assertThat(info.getFundingRate()).isEqualByComparingTo("0.0002");
        }
    }

    @Nested
    @DisplayName("fundingInfo 조회 테스트")
    class GetFundingInfoTest {

        @Test
        @DisplayName("등록되지 않은 pair는 null을 반환한다")
        void returnNullForUnregisteredPair() {
            registerTradingPair("BTCUSDT");
            assertThat(tracker.getFundingInfo("ETHUSDT")).isNull();
        }

        @Test
        @DisplayName("메시지가 오기 전 초기화되지 않은 상태는 null을 반환한다")
        void returnNullBeforeFirstMessage() {
            registerTradingPair("BTCUSDT");
            assertThat(tracker.getFundingInfo("BTCUSDT")).isNull();
        }

        @Test
        @DisplayName("메시지를 받아 초기화된 후에는 FundingInfo를 반환한다")
        void returnFundingInfoAfterInitialized() {
            String pair = "BTCUSDT";
            registerTradingPair(pair);
            tracker.processMessage(new FundingInfoMessage(
                    pair, Instant.now(),
                    new BigDecimal("50000"),
                    new BigDecimal("0.0001"),
                    Instant.now().plus(Duration.ofHours(4)),
                    Duration.ofHours(8)
            ));

            assertThat(tracker.getFundingInfo(pair)).isNotNull();
        }
    }



    @Nested
    @DisplayName("초기화 테스트")
    class StartTest {

        @Test
        @DisplayName("등록된 모든 tradingPair를 DataSource에 batchSubscribe한다")
        void batchSubscribeAllPairs() {

            FundingInfoMessageStream mockStream = mock(FundingInfoMessageStream.class);
            when(dataSource.batchSubscribe(anySet())).thenReturn(mockStream);

            // when
            tracker.start();

            // then
            ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
            verify(dataSource).batchSubscribe(captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrderElementsOf(DEFAULT_SUBLIST.keySet());
        }
    }

    @Nested
    @DisplayName("종료시")
    class StopTest {

        @Test
        @DisplayName("DataSource에 unsubscribe를 호출한다")
        void unsubscribeFromDataSource() {
            // given
            FundingInfoMessageStream mockStream = mock(FundingInfoMessageStream.class);
            when(dataSource.batchSubscribe(anySet())).thenReturn(mockStream);
            tracker.start();

            // when
            tracker.stop();

            // then
            verify(dataSource).unsubscribe(mockStream);
        }

        @Test
        @DisplayName("processTask가 있으면 취소한다")
        void cancelProcessTask() {
            // given
            Future<?> mockTask = mock(Future.class);
            doReturn(mockTask).when(ioExecutor).submit(any(Runnable.class));

            tracker.start();

            // when
            tracker.stop();

            // then
            verify(mockTask).cancel(true);
        }
    }

    void registerTradingPair(String tradingPair) {
        tracker.registerTradingPairs(Set.of(tradingPair));
    }
}
