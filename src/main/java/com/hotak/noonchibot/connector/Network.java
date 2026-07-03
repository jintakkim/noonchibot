package com.hotak.noonchibot.connector;

public enum Network {
    MAINNET,
    TESTNET;

    public boolean isTestnet() {
        return this == TESTNET;
    }
}
