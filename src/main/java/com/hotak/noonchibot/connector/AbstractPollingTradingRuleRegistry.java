package com.hotak.noonchibot.connector;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.trade.TradingRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPollingTradingRuleRegistry implements TradingRuleRegistry, LifecycleAware {
    private final RestAssistant restAssistant;
    private final TradingRuleParser parser;
    private final TaskScheduler scheduler;
    private final Duration pollingInterval;
    private volatile Map<String, TradingRule> tradingRules = Map.of();
    private volatile ScheduledFuture<?> scheduledFuture;

    @Override
    public TradingRule getTradingRule(String tradingPair) {
        return tradingRules.get(tradingPair);
    }

    @VisibleForTesting
    void update() {
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(createRequest());
        this.tradingRules = parser.parse(body).stream()
                .collect(Collectors.toMap(TradingRule::tradingPair, Function.identity()));
        log.debug("trading rule update completed.");
    }

    protected abstract RestRequest createRequest();

    @Override
    public void onStart() {
        update();
        scheduledFuture = scheduler.scheduleAtFixedRate(this::update, pollingInterval);
    }

    @Override
    public void onShutdown() {
        if(scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    @Override
    public int phase() {
        return Phases.TRADING_RULE_SETUP;
    }
}
