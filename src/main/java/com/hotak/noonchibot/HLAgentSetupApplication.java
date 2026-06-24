package com.hotak.noonchibot;

import com.hotak.noonchibot.connector.hyperliquid.AgentSetupConfig;
import com.hotak.noonchibot.connector.hyperliquid.AgentSetupRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class HLAgentSetupApplication {

    public static void main(String[] args) {
        try (var ctx = new AnnotationConfigApplicationContext(AgentSetupConfig.class)) {
            AgentSetupRunner runner = ctx.getBean(AgentSetupRunner.class);
            runner.run(args);
        }
        System.exit(0);
    }
}