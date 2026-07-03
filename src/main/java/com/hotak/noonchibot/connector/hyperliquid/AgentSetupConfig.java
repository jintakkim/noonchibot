package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.PassthroughAsyncThrottler;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class AgentSetupConfig {
    @Bean
    public AgentSetupRunner agentSetupRunner() {
        ObjectMapper objectMapper = new ObjectMapper();
        boolean isMainnet = !"testnet".equalsIgnoreCase(System.getenv("HYPERLIQUID_NETWORK"));
        String baseUrl = isMainnet ? DerivativeApiSpec.BASE_URL : DerivativeApiSpec.TESTNET_BASE_URL;
        RestAssistantImpl restAssistant = new RestAssistantImpl(
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .build(),
                List.of(),
                List.of(),
                null,
                new PassthroughAsyncThrottler(),
                objectMapper
        );
        return new AgentSetupRunner(new HyperliquidAgentGenerator(restAssistant, objectMapper, isMainnet));
    }
}
