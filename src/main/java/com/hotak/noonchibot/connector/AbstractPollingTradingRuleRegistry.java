package com.hotak.noonchibot.connector;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.TradingRule;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractPollingTradingRuleRegistry implements TradingRuleRegistry, LifecycleComponent {
    private final RestAssistant restAssistant;
    private final TradingRuleParser parser;
    private final TaskScheduler scheduler;
    private final Duration pollingInterval;
    private Map<String, TradingRule> tradingRules = Map.of();
    private ScheduledFuture<?> scheduledFuture;

    @Override
    public TradingRule getTradingRule(String tradingPair) {
        return tradingRules.get(tradingPair);
    }

    @VisibleForTesting
    void update() {
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(createRequest());
        this.tradingRules = parser.parse(body).stream()
                .collect(Collectors.toMap(TradingRule::tradingPair, Function.identity()));
    }

    protected abstract RestRequest createRequest();

    @Override
    public void start() {
        scheduledFuture = scheduler.scheduleAtFixedRate(this::update, pollingInterval);
    }

    @Override
    public void shutdown() {
        if(scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }
}
