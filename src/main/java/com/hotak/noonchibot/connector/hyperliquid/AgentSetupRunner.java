package com.hotak.noonchibot.connector.hyperliquid;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class
AgentSetupRunner {
    private final HyperliquidAgentGenerator generator;

    public void run(String[] args) {
        String masterKey = System.getenv("HYPERLIQUID_MASTER_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            System.err.println("HYPERLIQUID_MASTER_KEY env var required");
            System.exit(1);
        }

        String agentName = parseArg(args, "--name", "noonchibot");
        var agent = generator.generateAndRegister(masterKey, agentName);

        System.out.println();
        System.out.println("=== Agent registered ===");
        System.out.println("Address: " + agent.getAddress());
        System.out.println("Private key: " + agent.getPrivateKey());
        System.out.println();
        System.out.println("Add to environment:");
        System.out.println("  HYPERLIQUID_AGENT_ADDRESS=" + agent.getAddress());
        System.out.println("  HYPERLIQUID_AGENT_SECRET=" + agent.getPrivateKey());
    }

    private String parseArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) return args[i + 1];
        }
        return defaultValue;
    }
}