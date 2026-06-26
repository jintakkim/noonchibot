package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.OrderIdGenerator;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

class HyperliquidCloidGenerator implements OrderIdGenerator {
    private final AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    @Override
    public String createClientOrderId(boolean isBuy, String tradingPair, String prefix, int lengthLimit) {
        long next = nonce.updateAndGet(prev -> Math.max(System.currentTimeMillis(), prev + 1));
        String seed = (prefix == null ? "" : prefix) + ":" + isBuy + ":" + tradingPair + ":" + next;
        return "0x" + DigestUtils.md5DigestAsHex(seed.getBytes(StandardCharsets.UTF_8));
    }
}
