package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.datatype.TradingRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class PollingTradingRuleRegistry implements TradingRuleRegistry {
    private final RestAssistant restAssistant;
    private final TradingRuleParser parser;
    private final TaskScheduler scheduler;
    private final Duration pollingInterval;
    private volatile Map<String, TradingRule> tradingRules = Map.of();

    @Override
    public TradingRule getTradingRule(String tradingPair) {
        return tradingRules.get(tradingPair);
    }

    @PostConstruct
    public void startPolling() {
        update();
        scheduler.scheduleAtFixedRate(this::update, Instant.now().plus(pollingInterval), pollingInterval);
    }

    public void update() {
        JsonNode body = restAssistant.executeRequestAndGetJsonBody(createRequest());
        this.tradingRules = parser.parse(body).stream()
                .collect(Collectors.toMap(TradingRule::tradingPair, Function.identity()));
    }

    protected abstract RestRequest createRequest();
}
