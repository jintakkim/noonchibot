package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.PollingTradingRuleRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Map;

public class BybitTradingRuleRegistry extends PollingTradingRuleRegistry {
    private final String requestPath;

    public BybitTradingRuleRegistry(
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
