package com.hotak.noonchibot.connector;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  prefix + side + pair + id 로 이루어진 orderId를 반환
 *  lengthLimit을 초과한다면 nonce + botInstanceId(os 고유값을 통한 id)은 md5 hashing 후 축약
 */
public class StructuredOrderIdGenerator implements OrderIdGenerator {
    private final String botInstanceId;
    private final AtomicLong lastNonce = new AtomicLong(0);

    public StructuredOrderIdGenerator() {
        this.botInstanceId = createBotInstanceId();
    }

    @Override
    public String createClientOrderId(boolean isBuy, String tradingPair, String prefix, int lengthLimit) {
        String side = isBuy ? "B" : "S";
        String[] symbols = tradingPair.split("-");
        String base = symbols[0].toUpperCase();
        String quote = symbols[1].toUpperCase();
        String baseStr = "" + base.charAt(0) + base.charAt(base.length() - 1);
        String quoteStr = "" + quote.charAt(0) + quote.charAt(quote.length() - 1);
        String tsHex = Long.toHexString(createTrackingNonce());
        String idPrefix = (prefix != null ? prefix : "") + side + baseStr + quoteStr;
        String fullId = (idPrefix + tsHex + botInstanceId).replace("$", "");
        if (lengthLimit <= 0) {
            return fullId;
        }
        int suffixMaxLength = lengthLimit - idPrefix.length();
        if (suffixMaxLength < tsHex.length()) {
            String hash = md5(tsHex + botInstanceId);
            return idPrefix + hash.substring(0, suffixMaxLength);
        }
        return fullId.substring(0, Math.min(fullId.length(), lengthLimit));
    }

    private static String createBotInstanceId() {
        String osInfo = System.getProperty("os.name") + System.getProperty("os.version") + System.getProperty("os.arch");
        long pid = ProcessHandle.current().pid();
        long ppid = ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(0L);
        return md5(osInfo + "_pid:" + pid + "_ppid:" + ppid);
    }

    private static String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 순수 증가 카운터 + timestamp(ms precision)을 섞어 봇이 재시작될 때 다시 시작하여 이전 실행에서 생성한 주문ID와의 충돌 가능성을 제거
     */
    private long createTrackingNonce() {
        long candidate = System.currentTimeMillis();
        return lastNonce.updateAndGet(prev -> candidate > prev ? candidate : prev + 1);
    }

}
