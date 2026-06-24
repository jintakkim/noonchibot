package com.hotak.noonchibot.core.derivative;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.event.EventMetadata;
import com.hotak.noonchibot.core.event.EventSubscriber;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.Subscription;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class FundingInfoTracker implements LifecycleAware {
    private final Duration defaultFundingInterval;
    private final Map<String, FundingInfo> fundingInfos = new HashMap<>();
    private final String fundingCoin;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final EventSubscriber eventSubscriber;
    private final Set<Subscription> subscriptions = new HashSet<>();

    public FundingInfo getFundingInfo(String tradingPair) {
        FundingInfo fundingInfo = fundingInfos.get(tradingPair);
        if(fundingInfo == null || !fundingInfo.isInitialized()) return null;
        return fundingInfo;
    }

    @VisibleForTesting
    void processMessage(FundingInfoEvent.Received event, EventMetadata metadata) {
        String tradingPair = event.tradingPair();
        FundingInfo fundingInfo = fundingInfos.get(tradingPair);

        if (fundingInfo == null) {
            log.warn("등록되지 않은 tradingPair의 메시지는 처리할 수 없습니다");
            return;
        }
        fundingInfo.update(
                event.fundingInterval(),
                event.markPrice(),
                event.fundingRate(),
                event.nextFundingTime()
        );
    }

    @VisibleForTesting
    void processIntervalMessage(FundingInfoEvent.IntervalReceived event, EventMetadata metadata) {
        event.snapshot().forEach((tradingPair, interval) -> {
            FundingInfo fundingInfo = fundingInfos.get(tradingPair);
            if (fundingInfo == null) {
                log.warn("등록되지 않은 tradingPair의 메시지는 처리할 수 없습니다");
                return;
            }
            fundingInfo.update(interval);
        });
    }

    @VisibleForTesting
    void registerTradingPairs(Set<String> tradingPairs) {
        tradingPairs
                .forEach(tradingPair -> {
                    FundingInfo info = new FundingInfo(tradingPair, fundingCoin, defaultFundingInterval);
                    fundingInfos.put(tradingPair, info);
                });
    }

    @Override
    public void onStart() {
        Set<String> tradingPairs = new HashSet<>(tradingPairSymbolRegistry.getAllTradingPairs());
        registerTradingPairs(tradingPairs);
        subscriptions.add(
                eventSubscriber.subscribe(
                    FundingInfoEvent.Received.class,
                    this::processMessage,
                    ExecutionPolicy.sequential()
                ));
        subscriptions.add(
                eventSubscriber.subscribe(
                        FundingInfoEvent.IntervalReceived.class,
                        this::processIntervalMessage,
                        ExecutionPolicy.sequential()
                ));
    }

    @Override
    public void onShutdown() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }
}
