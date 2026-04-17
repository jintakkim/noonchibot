package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.orderbook.FundingInfoDataSource;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessageStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

@Slf4j
@RequiredArgsConstructor
public class FundingInfoTracker implements SmartLifecycle {
    private final Map<String, FundingInfo> fundingInfos = new HashMap<>();
    private final Duration defaultFundingInterval;
    private final String fundingCoin;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final FundingInfoDataSource fundingInfoDataSource;
    private final IoExecutor ioExecutor;
    private final MainExecutor mainExecutor;
    private FundingInfoMessageStream stream;
    private volatile boolean running = false;
    private volatile Future<?> processTask;

    public FundingInfo getFundingInfo(String tradingPair) {
        FundingInfo fundingInfo = fundingInfos.get(tradingPair);
        if(fundingInfo == null || !fundingInfo.isInitialized()) return null;
        return fundingInfo;
    }

    @VisibleForTesting
    void processMessage(FundingInfoMessage message) {
        String tradingPair = message.tradingPair();
        FundingInfo fundingInfo = fundingInfos.get(tradingPair);

        if (fundingInfo == null) {
            log.warn("등록되지 않은 tradingPair의 메시지는 처리할 수 없습니다");
            return;
        }
        fundingInfo.update(
                message.fundingInterval(),
                message.markPrice(),
                message.fundingRate(),
                message.nextFundingTime()
        );
    }

    @Override
    public void start() {
        Set<String> tradingPairs = new HashSet<>(tradingPairSymbolRegistry.getAllTradingPairs());
        registerTradingPairs(tradingPairs);
        stream = fundingInfoDataSource.batchSubscribe(tradingPairs);
        processTask = ioExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    FundingInfoMessage message = stream.take();
                    mainExecutor.submit(() -> processMessage(message));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        running = true;
    }

    @VisibleForTesting
    void registerTradingPairs(Set<String> tradingPairs) {
        tradingPairSymbolRegistry.getAllTradingPairs()
                .forEach(tradingPair -> {
                    FundingInfo info = new FundingInfo(tradingPair, fundingCoin, defaultFundingInterval);
                    fundingInfos.put(tradingPair, info);
                });
    }

    @Override
    public void stop() {
        running = false;
        fundingInfoDataSource.unsubscribe(stream);
        if(processTask != null) processTask.cancel(true);
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
