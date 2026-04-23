package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.AbstractPollingTradingRuleRegistry;
import com.hotak.noonchibot.connector.TradingRuleParser;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Map;

public class BybitTradingRuleRegistry extends AbstractPollingTradingRuleRegistry {
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

    // Bybit은 여러 심볼에 대해 info를 요청할 수 없다. 필드를 비우면 spot에 대한 모든 심볼이 반환된다.
    @Override
    protected RestRequest createRequest() {
        return RestRequest.builder()
                .method(HttpMethod.GET)
                .pathUrl(requestPath)
                .params(Map.of("category", "spot"))
                .build();
    }
}
