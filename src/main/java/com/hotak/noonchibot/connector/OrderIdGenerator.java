package com.hotak.noonchibot.connector;

@FunctionalInterface
public interface OrderIdGenerator {
    /**
     * @param isBuy
     * @param tradingPair
     * @param prefix 설정하지 않는다면 null
     * @param lengthLimit 설정하지 않는다면 null
     * @return
     */
    String createClientOrderId(boolean isBuy, String tradingPair, String prefix, int lengthLimit);
}
