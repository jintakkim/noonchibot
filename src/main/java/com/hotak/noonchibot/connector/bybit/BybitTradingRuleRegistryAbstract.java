package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.AbstractPollingTradingRuleRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Map;

public class BybitTradingRuleRegistryAbstract extends AbstractPollingTradingRuleRegistry {
    private final String requestPath;

    public BybitTradingRuleRegistryAbstract(
            RestAssistant restAssistant,
            TradingRuleParser parser,
            TaskScheduler scheduler,
            Duration pollingInterval,
            String requestPath
    ) {
        super(restAssistant, parser, scheduler, pollingInterval);
        this.requestPath = requestPath;
    }

    @Override
    protected RestRequest createRequest() {
        return RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(requestPath)
                .params(Map.of("category", "spot"))
                .build();
    }
}
