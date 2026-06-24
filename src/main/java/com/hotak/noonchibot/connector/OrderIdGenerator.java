package com.hotak.noonchibot.connector;

@FunctionalInterface
public interface OrderIdGenerator {
    /**
     * @param isBuy
     * @param tradingPair
     * @param prefix
     * @param lengthLimit 설정하지 않는다면 0보다 작은 값을 전달
     * @return
     */
    String createClientOrderId(boolean isBuy, String tradingPair, String prefix, int lengthLimit);
}
